# Pingbell DLQ / Reprocessing Policy

작성일: 2026-06-29

## 1. 목적

이 문서는 미래 Worker 분리 이후 check, incident, notification 단계에서 실패한 이벤트를 어떻게 재처리하고, 어떤 경우 DLQ 대상으로 분리할지 정책을 정리한다.

이번 작업은 설계 문서 작업이다. Kafka, DLQ topic/table, message producer, message consumer, 별도 Worker 애플리케이션은 구현하지 않는다.

## 2. 현재 원칙

현재 Pingbell은 단일 Spring Boot 애플리케이션 구조를 유지한다.

현재 원칙:

- Kafka를 도입하지 않는다.
- DLQ topic 또는 table을 만들지 않는다.
- 별도 Worker 애플리케이션을 만들지 않는다.
- 외부 URL 호출 실패는 Pingbell 내부 실패가 아니라 개별 monitor의 check 실패로 기록한다.
- 알림 발송 실패는 check 결과 저장과 incident 판정을 롤백하지 않는다.
- 알림 실패는 `NotificationHistory` 상태로 추적한다.
- retryable 알림 실패는 `RETRY_PENDING`으로 두고 Scheduler가 재시도한다.
- non-retryable 알림 실패 또는 retry exhausted는 `FAILED`로 둔다.
- 수동 재전송은 기존 실패 이력을 덮어쓰지 않고 새 이력을 만든다.

## 3. 용어 정의

| 용어 | 설명 |
| --- | --- |
| retryable | 같은 입력을 나중에 다시 처리하면 성공할 가능성이 있는 실패 |
| non-retryable | 재시도해도 성공 가능성이 낮고 데이터 수정이나 운영 조치가 필요한 실패 |
| retry exhausted | retryable 실패를 최대 횟수까지 재시도했지만 성공하지 못한 상태 |
| DLQ | 자동 처리에서 분리해 운영자가 확인하거나 수동 재처리할 실패 이벤트 저장소 |
| automatic reprocessing | 시스템이 backoff 기준에 따라 자동으로 다시 처리하는 방식 |
| manual reprocessing | 운영자 또는 사용자의 명시적 액션으로 다시 처리하는 방식 |

## 4. 미래 이벤트별 실패 분류

미래 Worker 분리 기준의 이벤트 흐름:

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

## 5. Check Worker 실패 기준

Check Worker는 `HealthCheckRequested`를 받아 URL 호출과 `CheckResult` 저장을 담당한다.

### 5.1 Retryable 실패

다음은 Check Worker 처리 자체의 retryable 실패다.

- DB 일시 장애로 monitor 조회에 실패했다.
- DB 일시 장애로 `CheckResult` 저장에 실패했다.
- DB 일시 장애로 monitor `nextCheckAt` 갱신에 실패했다.
- 이벤트 publish 또는 outbox 기록이 일시적으로 실패했다.
- Worker 프로세스가 처리 중 종료되었다.

처리 기준:

- 같은 `HealthCheckRequested`를 재처리할 수 있어야 한다.
- 중복 `CheckResult` 저장을 막기 위해 `requestEventId` 또는 `(monitorId, scheduledAt)`을 논리적 idempotency key로 본다.
- 재처리 전 monitor 최신 상태를 다시 조회한다.

### 5.2 Non-retryable 실패

다음은 Check Worker 단계의 non-retryable 실패다.

- monitor가 삭제되었다.
- monitor가 `PAUSED` 상태다.
- event payload의 monitor id가 존재하지 않는다.
- event payload schema가 잘못되어 필수 id를 확인할 수 없다.
- monitor 소유자나 필수 설정이 깨져 있다.

처리 기준:

- 삭제/일시정지된 monitor는 정상 skip으로 보고 DLQ 대상이 아니다.
- schema 불일치나 필수 데이터 불일치는 DLQ 후보로 둔다.
- 외부 URL 호출 실패는 non-retryable Worker 실패가 아니라 `CheckResult` 실패 상태로 저장한다.

## 6. Incident Detector 실패 기준

Incident Detector는 `HealthCheckCompleted`를 받아 monitor count 갱신, incident open / resolve, monitor 상태 전이를 담당한다.

### 6.1 Retryable 실패

다음은 retryable 실패다.

- DB 일시 장애로 `CheckResult`, monitor, incident 조회에 실패했다.
- monitor count 갱신 또는 incident 저장 중 DB 일시 장애가 발생했다.
- incident open / resolve 후 이벤트 publish 또는 outbox 기록이 실패했다.
- transaction conflict 또는 lock timeout이 발생했다.

처리 기준:

- 같은 `checkResultId`는 한 번만 incident 판정에 반영되어야 한다.
- 처리 완료 기록이 없으면 재시도할 수 있다.
- 재시도 시 DB의 `CheckResult`를 source of truth로 다시 조회한다.

### 6.2 Non-retryable 실패

다음은 non-retryable 실패다.

- `checkResultId`가 존재하지 않는다.
- `CheckResult`의 monitor와 event payload의 monitor id가 다르다.
- monitor가 삭제되었거나 `PAUSED` 상태라 더 이상 incident 판정 대상이 아니다.
- monitor가 `DOWN`인데 open incident가 없어 resolve할 수 없는 데이터 불일치가 있다.
- payload schema가 잘못되어 필수 id를 확인할 수 없다.

처리 기준:

- 삭제/일시정지 monitor는 skip으로 보고 DLQ 대상이 아니다.
- `checkResultId` 불일치, monitor 불일치, open incident 누락 같은 데이터 불일치는 DLQ 후보로 둔다.
- 운영자가 보정할 수 있도록 monitor id, check result id, incident id 후보를 남긴다.

## 7. Notification Worker 실패 기준

Notification Worker는 `IncidentOpened`, `IncidentResolved`를 받아 channel별 `NotificationHistory`와 `NotificationRequested`를 만든다.

### 7.1 Retryable 실패

다음은 retryable 실패다.

- DB 일시 장애로 incident, monitor, member, channel 조회에 실패했다.
- `NotificationHistory` 생성 중 DB 일시 장애가 발생했다.
- `NotificationRequested` outbox 기록 또는 publish가 일시적으로 실패했다.
- transaction conflict 또는 lock timeout이 발생했다.

처리 기준:

- 같은 incident, channel, notification type 이력은 한 번만 생성되어야 한다.
- 재시도 시 channel enabled 상태를 다시 조회한다.
- 이미 이력이 있으면 새 이력을 만들지 않는다.

### 7.2 Non-retryable 실패

다음은 non-retryable 실패다.

- incident가 존재하지 않는다.
- `IncidentOpened`를 처리하려는데 incident가 이미 `RESOLVED` 상태다.
- `IncidentResolved`를 처리하려는데 incident가 아직 `OPEN` 상태이거나 `resolvedAt`이 없다.
- incident monitor 또는 member가 존재하지 않는다.
- payload schema가 잘못되어 필수 id를 확인할 수 없다.

처리 기준:

- 늦게 도착한 `IncidentOpened`처럼 현재 상태와 맞지 않는 이벤트는 skip 또는 DLQ 후보로 분리한다.
- 원인 추적이 필요한 데이터 불일치는 DLQ 후보로 둔다.
- enabled channel이 없는 것은 실패가 아니다. 기존 channel이 있으면 알림 요청을 만들지 않고 종료한다.

## 8. Notification Sender 실패 기준

Notification Sender는 `NotificationRequested`를 받아 실제 EMAIL / SLACK / DISCORD 발송과 `NotificationHistory` 상태 반영을 담당한다.

### 8.1 Retryable 실패

다음은 retryable 실패다.

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

### 8.2 Non-retryable 실패

다음은 non-retryable 실패다.

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

### 8.3 Retry Exhausted

retryable 실패가 최대 재시도 횟수를 모두 사용하면 retry exhausted로 본다.

처리 기준:

- `NotificationHistory`를 `FAILED`로 변경한다.
- `retryable=false`로 종료한다.
- 실패 사유, 마지막 시도 시각, retry count를 남긴다.
- 미래 DLQ 도입 시 retry exhausted notification은 DLQ 후보가 된다.
- 사용자는 target 수정 또는 외부 장애 해소 후 수동 재전송할 수 있다.

## 9. DLQ 대상 후보

DLQ는 자동 처리에서 분리해 운영자가 확인해야 하는 실패 이벤트만 대상으로 삼는다.

DLQ 후보:

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

## 10. DLQ Payload 기준

DLQ payload에는 재처리와 원인 분석에 필요한 최소 정보만 넣는다.

포함 후보:

```text
dlqId
originalEventId
originalEventType
failedAt
failureStage
failureType
errorClass
errorMessage
retryCount
maxRetryCount
monitorId
checkResultId
incidentId
notificationHistoryId
channelId
channelType
notificationType
payloadVersion
manualReprocessable
```

제외할 값:

- monitor URL 원문
- email address 원문
- Slack webhook URL
- Discord webhook URL
- 복호화된 notification target
- JWT, API key, SMTP password 같은 secret
- Authorization header
- 사용자 비밀번호 또는 인증 토큰

보안 기준:

- DLQ는 운영자가 조회할 수 있으므로 secret 저장소가 아니다.
- 원인 분석은 id 기반으로 DB를 다시 조회하는 방식으로 한다.
- 실패 메시지에 URL이나 token이 포함될 수 있으면 redaction 후 저장한다.

## 11. 자동 재처리 기준

자동 재처리는 일시적 실패에만 적용한다.

자동 재처리 대상:

- DB 일시 장애
- lock timeout
- 외부 알림 서비스 5xx
- 외부 알림 서비스 429
- 네트워크 timeout
- sender 호출 중 일시적 ResourceAccessException

자동 재처리 비대상:

- payload schema 오류
- 필수 id 누락
- 권한/인증 실패
- target 복호화 실패
- 지원하지 않는 channel type
- 데이터 불일치로 운영 보정이 필요한 상태

기본 전략:

- 처음에는 고정 backoff를 사용한다.
- notification sender는 현재 정책처럼 1분, 5분 재시도 후 종료한다.
- Worker 이벤트 재처리는 미래 Kafka 도입 시 exponential backoff와 jitter를 검토한다.
- 자동 재처리 중에도 idempotency key로 중복 반영을 막는다.

## 12. 수동 재처리 기준

수동 재처리는 운영자 또는 사용자의 명시적 액션이 필요한 실패에 적용한다.

사용자 수동 재처리 대상:

- `FAILED` 알림 이력의 수동 재전송
- 잘못된 webhook URL 수정 후 재전송
- disabled channel 재활성화 후 재전송

운영자 수동 재처리 대상:

- DLQ에 쌓인 payload schema 오류
- monitor / checkResult / incident 데이터 불일치
- retry exhausted notification 이벤트
- outbox publish 실패가 장기간 해소되지 않은 이벤트

수동 재처리 원칙:

- 원본 이력을 덮어쓰지 않는다.
- 수동 재처리 이력에는 원본 id를 연결한다.
- 재처리 전에 현재 DB 상태를 다시 확인한다.
- 이미 성공 처리된 이벤트는 다시 처리하지 않는다.

## 13. 운영자가 확인해야 할 상태

운영자가 확인할 최소 상태:

- DLQ event count by event type
- DLQ event count by failure stage
- retry pending count
- retry exhausted count
- oldest DLQ event age
- notification failed count by channel type
- check worker processing failure count
- incident detector processing failure count
- notification worker processing failure count

운영 로그에 남길 값:

- `eventId`
- `eventType`
- `failureStage`
- `failureType`
- `monitorId`
- `checkResultId`
- `incidentId`
- `notificationHistoryId`
- `channelId`
- redacted error message

로그에 남기지 않을 값:

- monitor URL 원문
- webhook URL
- email address 원문
- 복호화된 target
- secret

## 14. 현재 구조를 유지하는 이유

지금은 Kafka와 DLQ 없이 현재 단일 앱 구조를 유지한다.

이유:

- 현재 MVP 기능은 단일 Spring Boot 앱에서 동작한다.
- 알림 재시도와 수동 재전송은 이미 `NotificationHistory` 상태 기반으로 검증 가능하다.
- Kafka와 DLQ를 먼저 도입하면 장애 판정과 알림 정책보다 운영 복잡도가 커진다.
- DLQ는 실제 message broker 또는 outbox 구조가 생긴 뒤 구현하는 편이 자연스럽다.
- 지금은 각 단계의 retryable / non-retryable 기준과 secret 제외 기준을 먼저 고정하는 것이 더 중요하다.

## 15. 이번 문서에서 하지 않은 일

- Kafka 의존성을 추가하지 않았다.
- DLQ topic 또는 DLQ table을 만들지 않았다.
- producer / consumer 코드를 만들지 않았다.
- retry worker를 만들지 않았다.
- DB schema를 변경하지 않았다.
- 운영 대시보드나 metric collector를 구현하지 않았다.

## 16. 다음 작업

다음 이슈는 `관측성 지표 목록 작성`이다.

다음 문서에서 다룰 내용:

- check success / failure / timeout / slow response count
- incident open / resolved count
- notification sent / failed / retry pending count
- retry exhausted / DLQ 후보 count
- p95 / p99 응답 시간 지표 후보
- 단일 앱 구조에서 먼저 볼 최소 운영 지표
