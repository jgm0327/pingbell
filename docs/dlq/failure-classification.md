# DLQ Failure Classification

## 미래 이벤트 흐름

미래 Worker 분리 기준의 이벤트 흐름은 다음과 같다.

```text
HealthCheckRequested
-> HealthCheckCompleted
-> IncidentOpened / IncidentResolved
-> NotificationRequested
-> NotificationSent / NotificationFailed
```

각 단계는 다음 원칙을 따른다.

- 처리 실패가 일시적 인프라 문제면 retryable로 본다.
- 입력 데이터가 잘못되었거나 권한/설정 문제면 non-retryable로 본다.
- 같은 이벤트가 중복 전달되어도 도메인 상태가 중복 반영되지 않아야 한다.
- 최종 실패 기록에는 원인, 대상 id, 재처리 가능 여부를 남기되 secret은 남기지 않는다.

## Check Worker 실패 기준

Check Worker는 `HealthCheckRequested`를 받아 URL 호출과 `CheckResult` 저장을 담당한다.

### Retryable 실패

- DB 일시 장애로 monitor 조회에 실패했다.
- DB 일시 장애로 `CheckResult` 저장에 실패했다.
- DB 일시 장애로 monitor `nextCheckAt` 갱신에 실패했다.
- 이벤트 publish 또는 outbox 기록이 일시적으로 실패했다.
- Worker 프로세스가 처리 중 종료되었다.

처리 기준:

- 같은 `HealthCheckRequested`를 재처리할 수 있어야 한다.
- 중복 `CheckResult` 저장을 막기 위해 `requestEventId` 또는 `(monitorId, scheduledAt)`을 논리적 idempotency key로 본다.
- 재처리 전 monitor 최신 상태를 다시 조회한다.

### Non-retryable 실패

- monitor가 삭제되었다.
- monitor가 `PAUSED` 상태다.
- event payload의 monitor id가 존재하지 않는다.
- event payload schema가 잘못되어 필수 id를 확인할 수 없다.
- monitor 소유자나 필수 설정이 깨져 있다.

처리 기준:

- 삭제/일시정지된 monitor는 정상 skip으로 보고 DLQ 대상이 아니다.
- schema 불일치나 필수 데이터 불일치는 DLQ 후보로 둔다.
- 외부 URL 호출 실패는 non-retryable Worker 실패가 아니라 `CheckResult` 실패 상태로 저장한다.

## Incident Detector 실패 기준

Incident Detector는 `HealthCheckCompleted`를 받아 monitor count 갱신, incident open / resolve, monitor 상태 전이를 담당한다.

### Retryable 실패

- DB 일시 장애로 `CheckResult`, monitor, incident 조회에 실패했다.
- monitor count 갱신 또는 incident 저장 중 DB 일시 장애가 발생했다.
- incident open / resolve 후 이벤트 publish 또는 outbox 기록이 실패했다.
- transaction conflict 또는 lock timeout이 발생했다.

처리 기준:

- 같은 `checkResultId`는 한 번만 incident 판정에 반영되어야 한다.
- 처리 완료 기록이 없으면 재시도할 수 있다.
- 재시도 시 DB의 `CheckResult`를 source of truth로 다시 조회한다.

### Non-retryable 실패

- `checkResultId`가 존재하지 않는다.
- `CheckResult`의 monitor와 event payload의 monitor id가 다르다.
- monitor가 삭제되었거나 `PAUSED` 상태라 더 이상 incident 판정 대상이 아니다.
- monitor가 `DOWN`인데 open incident가 없어 resolve할 수 없는 데이터 불일치가 있다.
- payload schema가 잘못되어 필수 id를 확인할 수 없다.

처리 기준:

- 삭제/일시정지 monitor는 skip으로 보고 DLQ 대상이 아니다.
- `checkResultId` 불일치, monitor 불일치, open incident 누락 같은 데이터 불일치는 DLQ 후보로 둔다.
- 운영자가 보정할 수 있도록 monitor id, check result id, incident id 후보를 남긴다.

## Notification Worker 실패 기준

Notification Worker는 `IncidentOpened`, `IncidentResolved`를 받아 channel별 `NotificationHistory`와 `NotificationRequested`를 만든다.

### Retryable 실패

- DB 일시 장애로 incident, monitor, member, channel 조회에 실패했다.
- `NotificationHistory` 생성 중 DB 일시 장애가 발생했다.
- `NotificationRequested` outbox 기록 또는 publish가 일시적으로 실패했다.
- transaction conflict 또는 lock timeout이 발생했다.

처리 기준:

- 같은 incident, channel, notification type 이력은 한 번만 생성되어야 한다.
- 재시도 시 channel enabled 상태를 다시 조회한다.
- 이미 이력이 있으면 새 이력을 만들지 않는다.

### Non-retryable 실패

- incident가 존재하지 않는다.
- `IncidentOpened`를 처리하려는데 incident가 이미 `RESOLVED` 상태다.
- `IncidentResolved`를 처리하려는데 incident가 아직 `OPEN` 상태이거나 `resolvedAt`이 없다.
- incident monitor 또는 member가 존재하지 않는다.
- payload schema가 잘못되어 필수 id를 확인할 수 없다.

처리 기준:

- 늦게 도착한 `IncidentOpened`처럼 현재 상태와 맞지 않는 이벤트는 skip 또는 DLQ 후보로 분리한다.
- 원인 추적이 필요한 데이터 불일치는 DLQ 후보로 둔다.
- enabled channel이 없는 것은 실패가 아니다. 기존 channel이 있으면 알림 요청을 만들지 않고 종료한다.

## Notification Sender 실패 기준

Notification Sender는 `NotificationRequested`를 받아 실제 EMAIL / SLACK / DISCORD 발송과 `NotificationHistory` 상태 반영을 담당한다.

### Retryable 실패

- EMAIL SMTP 연결 실패
- EMAIL SMTP timeout
- Slack / Discord webhook timeout
- Slack / Discord webhook 5xx 응답
- Slack / Discord webhook 429 응답
- DNS, connect timeout, read timeout 같은 네트워크 예외
- 외부 알림 서비스의 일시 장애

처리 기준:

- `NotificationHistory`를 `RETRY_PENDING`으로 변경한다.
- `nextRetryAt`을 설정한다.
- 현재 기본 정책은 최초 실패 후 1분, 1차 재시도 실패 후 5분이다.
- 최대 재시도 횟수는 현재 `maxRetryCount=2` 기준을 유지한다.

### Non-retryable 실패

- EMAIL target 형식 오류
- Slack / Discord webhook URL 형식 오류
- Slack / Discord webhook 400 응답
- Slack / Discord webhook 401 / 403 응답
- Slack / Discord webhook 404 응답
- channel이 disabled 상태다.
- target 복호화 실패
- 지원하지 않는 channel type
- message payload 생성 실패

처리 기준:

- `NotificationHistory`를 `FAILED`로 변경한다.
- 자동 재시도하지 않는다.
- 사용자가 target을 수정한 뒤 수동 재전송할 수 있는 상태로 둔다.

### Retry Exhausted

retryable 실패가 최대 재시도 횟수를 모두 사용하면 retry exhausted로 본다.

처리 기준:

- `NotificationHistory`를 `FAILED`로 변경한다.
- `retryable=false`로 종료한다.
- 실패 사유, 마지막 시도 시각, retry count를 남긴다.
- 미래 DLQ 도입 시 retry exhausted notification은 DLQ 후보가 된다.
- 사용자는 target 수정 또는 외부 장애 해소 후 수동 재전송할 수 있다.

## DLQ 대상 후보

DLQ는 자동 처리에서 분리해 운영자가 확인해야 하는 실패 이벤트만 대상으로 삼는다.

| 단계 | 이벤트 | DLQ 후보 조건 |
| --- | --- | --- |
| Check Worker | `HealthCheckRequested` | payload schema 오류, 필수 monitor id 누락, monitor 데이터 불일치 |
| Incident Detector | `HealthCheckCompleted` | checkResult 누락, monitor id 불일치, open incident 누락으로 resolve 불가 |
| Notification Worker | `IncidentOpened` / `IncidentResolved` | incident 누락, 상태 불일치, monitor/member 누락 |
| Notification Sender | `NotificationRequested` | retry exhausted, target 복호화 실패, 지원하지 않는 channel type |
| Retry Handler | `NotificationFailed` | 재시도 정책으로도 처리할 수 없는 최종 실패 |

DLQ 비대상:

- 외부 URL timeout, 4xx, 5xx 같은 monitor check 실패
- 삭제된 monitor에 대한 늦은 check 요청
- `PAUSED` monitor에 대한 늦은 check 요청
- enabled channel이 없어 알림 요청을 만들지 않는 경우
- 이미 처리된 중복 이벤트

