# DLQ Reprocessing Policy

## 자동 재처리 기준

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

## 수동 재처리 기준

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

## 현재 수동 재처리 구현 판단

현재 구현은 DLQ dry-run command와 단일 record 실제 재처리 command를 제공한다.

dry-run command는 DLQ record payload를 읽고 DB source of truth 기준으로 재처리 가능 여부만 판정한다. 실제 재처리 command는 dry-run 결과가 `REPROCESSABLE`이고 `--confirm-reprocess=true`가 명시된 단일 record만 source topic으로 다시 publish한다.

현재 구조를 유지하는 이유와 이번 정책에서 제외한 일은 [overview.md](overview.md)를 기준으로 한다.

현재 단일 record command만 제공하는 이유:

- DLQ payload에는 민감 정보를 넣지 않고, 보안 기준은 [payload-security.md](payload-security.md)를 따른다.
- 재처리 가능 여부는 payload 단독이 아니라 현재 DB 상태를 다시 조회해서 판단해야 한다.
- 잘못된 재처리 command는 이미 처리된 event를 다시 반영하거나, 오래된 event로 현재 상태를 덮어쓸 위험이 있다.
- 현재 사용자 수동 재처리 요구는 `NotificationHistory` 수동 재전송 API로 먼저 충족된다.
- 운영자용 DLQ 실제 재처리는 dry-run 검증 결과, DB 상태 확인, idempotency 확인을 먼저 통과한 단일 record에 한해 수동 command로만 실행한다.

따라서 현재 단계의 완료 기준은 수동 확인 절차, dry-run 판정, 단일 record 실제 재처리, list 조회, batch dry-run, 재처리/폐기 기준 문서화다. 여러 record를 한 번에 실제 재처리하는 batch reprocess는 구현하지 않는다.

## 재처리 가능 여부 판단

DLQ 메시지는 payload만 보고 바로 재처리하지 않는다. 반드시 현재 DB 상태를 다시 조회한다.

### 재처리해도 되는 조건

- payload schema가 현재 코드와 호환된다.
- 필수 id가 모두 존재한다.
- payload의 `memberId`, `monitorId`, `checkResultId`, `incidentId` 관계가 DB의 현재 관계와 일치한다.
- monitor가 삭제되지 않았고, 재처리 대상 상태가 현재 상태와 충돌하지 않는다.
- 같은 event 또는 같은 source entity가 이미 성공 처리되지 않았다.
- 재처리해도 idempotency 기준으로 중복 반영되지 않는다.
- 실패 원인이 일시적 DB 장애, lock timeout, consumer 프로세스 종료, 일시적 publish 실패처럼 현재 해소된 문제다.

### 폐기하거나 보정 후 처리해야 하는 조건

- payload schema가 깨져 필수 id를 파싱할 수 없다.
- payload의 id 관계가 DB와 불일치한다.
- monitor가 이미 삭제되었다.
- monitor가 `PAUSED` 상태이고 재처리해도 현재 운영 상태와 맞지 않는다.
- `HealthCheckCompleted`의 `checkResultId`가 존재하지 않는다.
- `NotificationRequested`의 `incidentId`가 존재하지 않는다.
- 이미 같은 `checkResultId`로 incident 판정이 완료되었다.
- 이미 같은 incident, channel, notification type의 알림 이력이 생성되었다.
- 실패 원인이 target 복호화 실패, 지원하지 않는 channel type, 권한/설정 오류처럼 데이터 보정이 먼저 필요한 문제다.

## 실제 재처리 정책

실제 재처리는 dry-run 결과가 `REPROCESSABLE`인 record만 대상으로 한다. `SKIP_ALREADY_PROCESSED`와 `NOT_REPROCESSABLE`은 실제 재처리 command 대상이 아니다.

### 공통 실행 원칙

- dry-run을 먼저 실행하지 않은 record는 실제 재처리하지 않는다.
- 실제 재처리 command는 기본적으로 disabled 상태로 둔다.
- 실제 재처리는 `--confirm-reprocess=true` 같은 명시적 confirm 옵션이 있을 때만 실행한다.
- 실제 재처리 command는 한 번에 하나의 topic / partition / offset만 처리한다.
- batch 재처리는 별도 이슈에서 다룬다.
- direct mode 동작은 변경하지 않는다.
- 재처리 중에도 현재 DB 상태를 다시 조회한다.
- 재처리 성공/실패 결과는 운영 로그에 남긴다.
- 로그에 남기면 안 되는 값은 [payload-security.md](payload-security.md)의 민감 정보 제외 기준을 따른다.

### Topic별 재처리 방식

| DLQ topic | 실제 재처리 방식 | 이유 |
| --- | --- | --- |
| `pingbell.health-check.requested.dlq` | 원본 source topic으로 re-publish 우선 | 기존 Check Worker 흐름을 그대로 타게 해 URL 호출, `CheckResult` 저장, `HealthCheckCompleted` publish 책임을 유지한다. |
| `pingbell.health-check.completed.dlq` | 원본 source topic으로 re-publish 우선 | Incident Detector consumer가 `checkResultId` idempotency를 다시 확인하므로 기존 중복 방지 기준을 재사용한다. |
| `pingbell.notification.requested.dlq` | 원본 source topic으로 re-publish 우선 | Notification Sender consumer가 기존 알림 발송과 `NotificationHistory` 상태 반영 흐름을 그대로 수행하게 한다. |

service 직접 호출은 기본 금지한다.

예외적으로 service 직접 호출을 검토할 수 있는 경우:

- Kafka cluster 장애가 장기간 지속되어 re-publish 자체가 불가능하다.
- 운영자가 같은 DB transaction 경계에서 보정 작업과 재처리를 함께 해야 한다.
- 별도 이슈에서 service 직접 호출용 idempotency, audit log, 실패 처리 기준을 먼저 고정했다.

### Topic별 금지 조건

`HealthCheckRequested` 실제 재처리 금지:

- monitor가 삭제되었다.
- monitor가 `PAUSED` 상태다.
- monitor의 `nextCheckAt`이 event `scheduledAt`보다 이후다.
- payload의 `monitorId`, `memberId`가 DB와 불일치한다.

`HealthCheckCompleted` 실제 재처리 금지:

- `checkResultId`가 존재하지 않는다.
- `CheckResult`의 monitor 관계가 payload와 불일치한다.
- `incident_detection_processed_check_results`에 같은 `checkResultId`가 이미 있다.
- monitor가 삭제되었거나 `PAUSED` 상태다.

`NotificationRequested` 실제 재처리 금지:

- `incidentId`가 존재하지 않는다.
- incident의 monitor/member 관계가 payload와 불일치한다.
- 같은 incident와 `notificationType`에 대한 `NotificationHistory`가 이미 존재한다.
- `notificationType`이 현재 코드에서 지원하지 않는 값이다.

### 재처리 이력 기준

현재 단계에서는 별도 DLQ table을 만들지 않는다. 실제 재처리 command를 구현할 때는 최소한 다음 값을 운영 로그에 남긴다.

```text
operation=DLQ_REPROCESS
dryRunStatus
topic
partition
offset
payloadType
eventId
monitorId
checkResultId
incidentId
notificationType
result
reason
executedAt
```

DB table이 필요한 시점:

- 같은 record의 재처리 시도 횟수를 장기간 추적해야 한다.
- 운영자별 실행자를 저장해야 한다.
- 여러 record를 batch로 재처리해야 한다.
- 재처리 성공/실패 이력을 UI에서 조회해야 한다.

그 전까지는 command 로그와 Kafka offset 기준으로 최소 운영한다.

### 재처리 실패 처리

실제 재처리 command가 실패하면 같은 command 안에서 다시 DLQ로 보내지 않는다.

- re-publish 실패는 command 실패로 종료하고 운영 로그에 남긴다.
- consumer가 re-published event 처리에 실패하면 기존 Kafka retry/DLQ 흐름을 따른다.
- 실패한 record를 command가 자체 retry하지 않는다.
- 반복 실패하면 원인을 보정한 뒤 dry-run부터 다시 실행한다.
