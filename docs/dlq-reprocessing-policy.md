# Pingbell DLQ / Reprocessing Policy

작성일: 2026-07-01

## 1. 목적

이 문서는 Kafka mode에서 DLQ에 쌓인 메시지를 운영자가 어떻게 확인하고, 어떤 기준으로 재처리하거나 폐기할지 정리한다.

현재 Pingbell은 별도 Worker 애플리케이션을 분리하지 않고, 단일 Spring Boot 앱 안에서 Kafka listener와 DLQ topic을 사용한다. 이번 문서는 로컬 Docker Compose 환경에서 확인 가능한 최소 운영 절차와 재처리 판단 기준을 고정한다.

## 2. 현재 원칙

현재 Pingbell은 단일 Spring Boot 애플리케이션 구조를 유지하되, `PINGBELL_CHECK_DISPATCH_MODE=kafka`일 때 Kafka event flow를 사용한다.

현재 원칙:

- direct mode 동작은 변경하지 않는다.
- 별도 Worker 애플리케이션을 만들지 않는다.
- DLQ table 또는 운영 UI는 만들지 않는다.
- DLQ topic은 source topic 이름 뒤에 `.dlq`를 붙인다.
- DLQ record는 원본 Kafka event payload를 유지한다.
- DLQ header에는 원본 topic, partition, offset, exception class만 남긴다.
- exception stack trace, exception message, secret은 DLQ header에 남기지 않는다.
- 외부 URL 호출 실패는 Pingbell 내부 실패가 아니라 개별 monitor의 check 실패로 기록한다.
- 알림 발송 실패는 check 결과 저장과 incident 판정을 롤백하지 않는다.
- 알림 실패는 `NotificationHistory` 상태로 추적한다.
- retryable 알림 실패는 `RETRY_PENDING`으로 두고 Scheduler가 재시도한다.
- non-retryable 알림 실패 또는 retry exhausted는 `FAILED`로 둔다.
- 수동 재전송은 기존 실패 이력을 덮어쓰지 않고 새 이력을 만든다.

현재 DLQ topic:

```text
pingbell.health-check.requested.dlq
pingbell.health-check.completed.dlq
pingbell.notification.requested.dlq
```

현재 consumer non-retryable exception:

```text
IllegalArgumentException
ClassCastException
NoSuchElementException
```

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

### 12.1 현재 수동 재처리 구현 판단

현재 구현은 DLQ dry-run command와 단일 record 실제 재처리 command를 제공한다.

dry-run command는 DLQ record payload를 읽고 DB source of truth 기준으로 재처리 가능 여부만 판정한다. 실제 재처리 command는 dry-run 결과가 `REPROCESSABLE`이고 `--confirm-reprocess=true`가 명시된 단일 record만 source topic으로 다시 publish한다. service 재호출, 자동 재처리 스케줄러, batch 재처리는 구현하지 않는다.

이유:

- 현재 DLQ payload에는 monitor URL, notification target, webhook URL, email, secret을 넣지 않는다.
- 재처리 가능 여부는 payload 단독이 아니라 현재 DB 상태를 다시 조회해서 판단해야 한다.
- 잘못된 재처리 command는 이미 처리된 event를 다시 반영하거나, 오래된 event로 현재 상태를 덮어쓸 위험이 있다.
- 현재 사용자 수동 재처리 요구는 `NotificationHistory` 수동 재전송 API로 먼저 충족된다.
- 운영자용 DLQ 실제 재처리는 dry-run 검증 결과, DB 상태 확인, idempotency 확인을 먼저 통과한 단일 record에 한해 수동 command로만 실행한다.

따라서 현재 단계의 완료 기준은 수동 확인 절차, dry-run 판정, 단일 record 실제 재처리, list 조회, batch dry-run, 재처리/폐기 기준 문서화다. 여러 record를 한 번에 실제 재처리하는 batch reprocess는 구현하지 않는다.

## 13. DLQ 수동 확인 절차

### 13.1 Kafka 실행

```powershell
docker compose up -d kafka
```

Kafka mode로 애플리케이션을 실행한다.

```powershell
.\gradlew.bat bootRun --args="--pingbell.check.dispatch-mode=kafka"
```

### 13.2 Topic별 DLQ 메시지 확인

`HealthCheckRequested` consumer 실패 확인:

```powershell
docker exec -it pingbell-kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic pingbell.health-check.requested.dlq --from-beginning --property print.headers=true
```

`HealthCheckCompleted` consumer 실패 확인:

```powershell
docker exec -it pingbell-kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic pingbell.health-check.completed.dlq --from-beginning --property print.headers=true
```

`NotificationRequested` consumer 실패 확인:

```powershell
docker exec -it pingbell-kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic pingbell.notification.requested.dlq --from-beginning --property print.headers=true
```

확인할 header:

```text
pingbell-dlq-original-topic
pingbell-dlq-original-partition
pingbell-dlq-original-offset
pingbell-dlq-exception
```

확인할 payload id:

| DLQ topic | 확인할 id |
| --- | --- |
| `pingbell.health-check.requested.dlq` | `eventId`, `monitorId`, `memberId`, `scheduledAt` |
| `pingbell.health-check.completed.dlq` | `eventId`, `requestEventId`, `monitorId`, `memberId`, `checkResultId` |
| `pingbell.notification.requested.dlq` | `eventId`, `incidentId`, `monitorId`, `memberId`, `notificationType` |

### 13.3 DLQ dry-run command

DLQ record의 재처리 가능 여부만 확인한다. 이 command는 데이터를 변경하거나 record를 다시 publish하지 않는다.

```powershell
.\gradlew.bat bootRun --args="--pingbell.dlq.dry-run.enabled=true --pingbell.dlq.dry-run.topic=pingbell.health-check.requested.dlq --pingbell.dlq.dry-run.partition=0 --pingbell.dlq.dry-run.offset=0"
```

출력 예시:

```text
DLQ_DRY_RUN topic=pingbell.health-check.requested.dlq partition=0 offset=0 payloadType=HealthCheckRequestedEvent status=REPROCESSABLE reason=HealthCheckRequested can be retried as a dry-run decision
```

status 의미:

| status | 의미 |
| --- | --- |
| `REPROCESSABLE` | 현재 DB 기준으로 재처리 후보가 될 수 있다. |
| `SKIP_ALREADY_PROCESSED` | 이미 처리됐거나 현재 상태가 더 최신이라 재처리하지 않는다. |
| `NOT_REPROCESSABLE` | payload schema 오류, DB 관계 불일치, 삭제/일시정지 등으로 재처리하면 안 된다. |

### 13.4 DLQ list command

DLQ topic의 record를 제한된 개수만 조회해 topic, partition, offset, payload type, 주요 id, 읽기 가능 여부를 확인한다. 실제 재처리 가능 여부는 판단하지 않고, record를 다시 publish하지 않는다.

```powershell
.\gradlew.bat bootRun --args="--pingbell.dlq.list.enabled=true --pingbell.dlq.list.topic=pingbell.health-check.requested.dlq --pingbell.dlq.list.max-records=10"
```

출력 예시:

```text
DLQ_RECORD topic=pingbell.health-check.requested.dlq partition=0 offset=0 payloadType=HealthCheckRequestedEvent primaryIds="eventId=..., monitorId=10, memberId=1" readStatus=READABLE reason=
DLQ_RECORD topic=pingbell.health-check.requested.dlq partition=0 offset=1 payloadType=INVALID_PAYLOAD primaryIds="" readStatus=UNREADABLE reason=Invalid DLQ payload schema. topic=pingbell.health-check.requested.dlq
DLQ_RECORD topic=pingbell.health-check.requested.dlq partition=0 offset=2 payloadType=TOMBSTONE primaryIds="" readStatus=UNREADABLE reason=DLQ record value is null
DLQ_LIST_SUMMARY topic=pingbell.health-check.requested.dlq requestedMax=10 returned=3
```

`readStatus=UNREADABLE`인 record는 payload를 현재 코드의 event schema로 읽을 수 없다는 뜻이다. invalid payload는 `Invalid DLQ payload schema...`, tombstone record는 `DLQ record value is null`로 표시된다.

### 13.5 DLQ batch dry-run command

DLQ topic의 record를 `max-records` 개수만큼 읽고 각 record에 대해 dry-run 판단만 수행한다. 이 command는 실제 reprocess를 수행하지 않고 source topic으로 publish하지 않는다.

```powershell
.\gradlew.bat bootRun --args="--pingbell.dlq.batch-dry-run.enabled=true --pingbell.dlq.batch-dry-run.topic=pingbell.health-check.requested.dlq --pingbell.dlq.batch-dry-run.max-records=10"
```

출력 예시:

```text
DLQ_BATCH_DRY_RUN topic=pingbell.health-check.requested.dlq partition=0 offset=0 payloadType=HealthCheckRequestedEvent primaryIds="eventId=..., monitorId=10, memberId=1" status=REPROCESSABLE reason=HealthCheckRequested can be retried as a dry-run decision
DLQ_BATCH_DRY_RUN topic=pingbell.health-check.requested.dlq partition=0 offset=1 payloadType=INVALID_PAYLOAD primaryIds="" status=NOT_REPROCESSABLE reason=Invalid DLQ payload schema. topic=pingbell.health-check.requested.dlq
DLQ_BATCH_DRY_RUN topic=pingbell.health-check.requested.dlq partition=0 offset=2 payloadType=TOMBSTONE primaryIds="" status=NOT_REPROCESSABLE reason=DLQ record value is null
DLQ_BATCH_DRY_RUN_SUMMARY topic=pingbell.health-check.requested.dlq requestedMax=10 returned=3 REPROCESSABLE=1 SKIP_ALREADY_PROCESSED=0 NOT_REPROCESSABLE=2
```

invalid payload와 tombstone record는 batch dry-run에서 `NOT_REPROCESSABLE`로 집계된다. 이 경우 `DlqDryRunService`의 DB 기반 판단까지 가지 않고, record 읽기 단계의 오류 사유를 그대로 결과에 남긴다.

### 13.6 운영 실행 순서

실제 운영 또는 운영 유사 환경에서는 다음 순서를 지킨다.

1. Kafka와 애플리케이션을 `kafka` dispatch mode로 실행한다.
2. `list` command로 DLQ topic에 쌓인 record의 partition / offset / payload type / 주요 id를 확인한다.
3. 필요하면 `batch-dry-run` command로 제한된 개수의 record를 한 번에 점검한다.
4. 실제 재처리가 필요한 record 후보를 하나 고른 뒤 `dry-run` command를 단일 partition / offset 기준으로 다시 실행한다.
5. dry-run 결과가 `REPROCESSABLE`이고 현재 DB 상태와 idempotency 기준을 확인한 경우에만 단일 `reprocess` command를 실행한다.
6. `SKIP_ALREADY_PROCESSED`, `NOT_REPROCESSABLE`, invalid payload, tombstone record는 실제 reprocess 대상에서 제외한다.

`max-records`는 운영자가 한 번에 검토할 수 있는 개수로 제한한다. 기본값은 10이며, 원인 분석 중에는 10에서 시작하고 필요한 경우 50 이하로만 늘린다. 큰 값을 사용하면 운영 로그가 과도하게 길어지고 오래된 record를 충분한 검토 없이 재처리 후보로 착각할 수 있다.

## 14. 재처리 가능 여부 판단

DLQ 메시지는 payload만 보고 바로 재처리하지 않는다. 반드시 현재 DB 상태를 다시 조회한다.

### 14.1 재처리해도 되는 조건

- payload schema가 현재 코드와 호환된다.
- 필수 id가 모두 존재한다.
- payload의 `memberId`, `monitorId`, `checkResultId`, `incidentId` 관계가 DB의 현재 관계와 일치한다.
- monitor가 삭제되지 않았고, 재처리 대상 상태가 현재 상태와 충돌하지 않는다.
- 같은 event 또는 같은 source entity가 이미 성공 처리되지 않았다.
- 재처리해도 idempotency 기준으로 중복 반영되지 않는다.
- 실패 원인이 일시적 DB 장애, lock timeout, consumer 프로세스 종료, 일시적 publish 실패처럼 현재 해소된 문제다.

### 14.2 폐기하거나 보정 후 처리해야 하는 조건

- payload schema가 깨져 필수 id를 파싱할 수 없다.
- payload의 id 관계가 DB와 불일치한다.
- monitor가 이미 삭제되었다.
- monitor가 `PAUSED` 상태이고 재처리해도 현재 운영 상태와 맞지 않는다.
- `HealthCheckCompleted`의 `checkResultId`가 존재하지 않는다.
- `NotificationRequested`의 `incidentId`가 존재하지 않는다.
- 이미 같은 `checkResultId`로 incident 판정이 완료되었다.
- 이미 같은 incident, channel, notification type의 알림 이력이 생성되었다.
- 실패 원인이 target 복호화 실패, 지원하지 않는 channel type, 권한/설정 오류처럼 데이터 보정이 먼저 필요한 문제다.

### 14.3 Payload와 DB 기준 복구 가능성

현재 Kafka payload와 DLQ payload에는 secret을 넣지 않는다.

| 항목 | payload 포함 여부 | 재처리 시 복구 기준 |
| --- | --- | --- |
| monitor URL | 포함하지 않음 | `monitorId`로 `Monitor`를 다시 조회한다. |
| notification target | 포함하지 않음 | `memberId`와 incident 기준으로 활성 `NotificationChannel`을 다시 조회한다. |
| webhook URL | 포함하지 않음 | 암호화된 DB target을 복호화해 사용한다. |
| email address | 포함하지 않음 | EMAIL channel target 또는 member email 기준으로 재구성한다. |
| secret / token | 포함하지 않음 | 환경변수와 DB 암호화 값을 사용한다. |

결론:

- 정상적인 id 관계가 유지되어 있으면 DB source of truth 기준으로 복구 가능하다.
- payload에 id가 없거나 DB 관계가 깨진 경우에는 자동/수동 재처리보다 데이터 보정 또는 폐기가 우선이다.

## 15. 실제 재처리 정책

실제 재처리는 dry-run 결과가 `REPROCESSABLE`인 record만 대상으로 한다. `SKIP_ALREADY_PROCESSED`와 `NOT_REPROCESSABLE`은 실제 재처리 command 대상이 아니다.

### 15.1 공통 실행 원칙

- dry-run을 먼저 실행하지 않은 record는 실제 재처리하지 않는다.
- 실제 재처리 command는 기본적으로 disabled 상태로 둔다.
- 실제 재처리는 `--confirm-reprocess=true` 같은 명시적 confirm 옵션이 있을 때만 실행한다.
- 실제 재처리 command는 한 번에 하나의 topic / partition / offset만 처리한다.
- batch 재처리는 별도 이슈에서 다룬다.
- direct mode 동작은 변경하지 않는다.
- 재처리 중에도 현재 DB 상태를 다시 조회한다.
- 재처리 성공/실패 결과는 운영 로그에 남긴다.
- monitor URL, webhook URL, email address, 복호화된 target, secret은 로그에 남기지 않는다.

### 15.2 Topic별 재처리 방식

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

### 15.3 Topic별 금지 조건

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

### 15.4 재처리 이력 기준

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

### 15.5 재처리 실패 처리

실제 재처리 command가 실패하면 같은 command 안에서 다시 DLQ로 보내지 않는다.

처리 기준:

- re-publish 실패는 command 실패로 종료하고 운영 로그에 남긴다.
- consumer가 re-published event 처리에 실패하면 기존 Kafka retry/DLQ 흐름을 따른다.
- 실패한 record를 command가 자체 retry하지 않는다.
- 반복 실패하면 원인을 보정한 뒤 dry-run부터 다시 실행한다.

### 15.6 실제 재처리 command

단일 DLQ record만 원본 source topic으로 다시 publish한다. 실행 직전에 dry-run 검증을 다시 수행하며, 결과가 `REPROCESSABLE`이 아니면 publish하지 않는다.

```powershell
.\gradlew.bat bootRun --args="--pingbell.dlq.reprocess.enabled=true --pingbell.dlq.reprocess.topic=pingbell.health-check.requested.dlq --pingbell.dlq.reprocess.partition=0 --pingbell.dlq.reprocess.offset=0 --confirm-reprocess=true"
```

출력 예시:

```text
DLQ_REPROCESS topic=pingbell.health-check.requested.dlq partition=0 offset=0 sourceTopic=pingbell.health-check.requested payloadType=HealthCheckRequestedEvent messageKey=10 status=REPROCESSABLE reason=HealthCheckRequested can be retried as a dry-run decision
```

실행 조건:

- `--confirm-reprocess=true`가 있어야 한다.
- topic은 `.dlq`로 끝나야 한다.
- dry-run 결과가 `REPROCESSABLE`이어야 한다.
- service 직접 호출은 하지 않는다.
- source topic은 DLQ topic에서 `.dlq` suffix를 제거해 결정한다.

## 16. DLQ 운영 로그 보관 기준

현재 단계에서는 DLQ command 실행 결과를 별도 table에 저장하지 않는다. 운영자는 애플리케이션 표준 출력 또는 운영 로그 수집 도구에서 `DLQ_*` operation log를 검색해 record 흐름을 추적한다.

공통 보관 원칙:

- 모든 DLQ command 로그는 한 줄 key-value 형식으로 남긴다.
- record 식별자는 `topic`, `partition`, `offset` 조합을 기준으로 한다.
- payload 식별자는 `payloadType`과 `primaryIds`를 기준으로 한다.
- command 결과는 `status`, `readStatus`, `result`, `reason` 중 해당 command가 제공하는 필드로 판단한다.
- `reason`은 운영 판단에 필요한 짧은 사유만 남기고 payload 원문이나 민감 정보를 포함하지 않는다.
- 같은 record의 흐름은 `topic + partition + offset`으로 `DLQ_RECORD`, `DLQ_BATCH_DRY_RUN`, `DLQ_DRY_RUN`, `DLQ_REPROCESS` 로그를 함께 조회한다.
- batch command summary는 개별 record 추적용이 아니라 실행 범위와 집계 확인용으로 보관한다.

### 16.1 Command별 필수 로그 필드

| 로그 | 목적 | 필수 필드 | 추적 기준 |
| --- | --- | --- | --- |
| `DLQ_RECORD` | DLQ topic record 목록 확인 | `topic`, `partition`, `offset`, `payloadType`, `primaryIds`, `readStatus`, `reason` | 특정 record가 읽을 수 있는 payload인지 확인한다. |
| `DLQ_LIST_SUMMARY` | list command 실행 범위 확인 | `topic`, `requestedMax`, `returned`, `status`, `reason` | list command가 몇 개 record를 조회했는지 확인한다. |
| `DLQ_BATCH_DRY_RUN` | 여러 record의 재처리 가능성 사전 판단 | `topic`, `partition`, `offset`, `payloadType`, `primaryIds`, `status`, `reason` | batch 검토 중 재처리 후보를 고른다. |
| `DLQ_BATCH_DRY_RUN_SUMMARY` | batch dry-run 집계 확인 | `topic`, `requestedMax`, `returned`, `REPROCESSABLE`, `SKIP_ALREADY_PROCESSED`, `NOT_REPROCESSABLE`, `status`, `reason` | 전체 batch 결과 비율과 실패 여부를 확인한다. |
| `DLQ_DRY_RUN` | 단일 record 재처리 가능성 최종 판단 | `topic`, `partition`, `offset`, `payloadType`, `status`, `reason` | 실제 reprocess 직전 단일 record 상태를 확인한다. |
| `DLQ_REPROCESS` | 단일 record 실제 재처리 실행 결과 | `topic`, `partition`, `offset`, `sourceTopic`, `payloadType`, `messageKey`, `status`, `reason` | 어떤 DLQ record가 어느 source topic으로 다시 publish됐는지 확인한다. |

`status=FAILED`인 summary 또는 reprocess 로그는 command 실행 자체가 실패했다는 뜻이다. 이 경우 같은 `topic`, `partition`, `offset`으로 `list`와 `dry-run`을 다시 실행하기 전에 실패 원인을 먼저 보정한다.

### 16.2 Topic별 primaryIds 기준

`primaryIds`는 payload 원문을 로그에 남기지 않고도 DB source of truth를 다시 조회하기 위한 최소 식별자다.

| payloadType | primaryIds | DB 확인 기준 |
| --- | --- | --- |
| `HealthCheckRequestedEvent` | `eventId`, `monitorId`, `memberId` | monitor 존재 여부, monitor owner, monitor status, `nextCheckAt` 충돌 여부 |
| `HealthCheckCompletedEvent` | `eventId`, `requestEventId`, `monitorId`, `memberId`, `checkResultId` | check result 존재 여부, monitor 관계, incident 판정 처리 여부 |
| `NotificationRequestedEvent` | `eventId`, `incidentId`, `monitorId`, `memberId`, `notificationType` | incident 존재 여부, monitor/member 관계, 같은 incident와 notification type의 알림 이력 여부 |

invalid payload와 tombstone record는 `primaryIds`가 비어 있을 수 있다. 이 경우 `topic`, `partition`, `offset`, `payloadType`, `reason`만으로 record를 식별하고 실제 reprocess 대상에서 제외한다.

### 16.3 민감 정보 제외 기준

다음 값은 DLQ command 로그, summary 로그, reason, primaryIds 어디에도 남기지 않는다.

- monitor URL 원문
- email address 원문
- Slack webhook URL
- Discord webhook URL
- 복호화된 notification target
- JWT, API key, SMTP password, Authorization header
- 사용자 비밀번호 또는 인증 토큰
- payload 원문 전체

로그에 남겨도 되는 값:

- Kafka `topic`, `partition`, `offset`
- source topic 이름
- event id와 도메인 id
- enum 값인 `payloadType`, `notificationType`, `status`, `readStatus`
- secret을 포함하지 않는 exception class 또는 짧은 실패 분류

운영자가 원인 분석을 위해 실제 URL, email, webhook target이 필요하면 로그가 아니라 DB와 secret 저장소의 권한 있는 조회 절차를 사용한다.

### 16.4 Record 처리 흐름 추적 방법

단일 record를 추적할 때는 다음 순서로 운영 로그를 조회한다.

1. `DLQ_RECORD topic={topic} partition={partition} offset={offset}` 로그로 payloadType, primaryIds, readStatus를 확인한다.
2. `DLQ_BATCH_DRY_RUN topic={topic} partition={partition} offset={offset}` 로그가 있으면 batch 검토 당시 status와 reason을 확인한다.
3. 실제 재처리 후보라면 `DLQ_DRY_RUN topic={topic} partition={partition} offset={offset}` 로그로 최신 DB 기준 status를 확인한다.
4. 재처리를 실행했다면 `DLQ_REPROCESS topic={topic} partition={partition} offset={offset}` 로그로 sourceTopic, messageKey, status를 확인한다.
5. re-publish 이후 consumer 처리 결과는 source topic consumer 로그와 도메인 DB 상태로 확인한다.

추적 예시:

```text
DLQ_RECORD topic=pingbell.health-check.completed.dlq partition=0 offset=4 payloadType=HealthCheckCompletedEvent primaryIds="eventId=... requestEventId=... monitorId=10 memberId=1 checkResultId=30" readStatus=READABLE reason=
DLQ_BATCH_DRY_RUN topic=pingbell.health-check.completed.dlq partition=0 offset=4 payloadType=HealthCheckCompletedEvent primaryIds="eventId=... requestEventId=... monitorId=10 memberId=1 checkResultId=30" status=REPROCESSABLE reason=HealthCheckCompleted can be retried as a dry-run decision
DLQ_DRY_RUN topic=pingbell.health-check.completed.dlq partition=0 offset=4 payloadType=HealthCheckCompletedEvent status=REPROCESSABLE reason=HealthCheckCompleted can be retried as a dry-run decision
DLQ_REPROCESS topic=pingbell.health-check.completed.dlq partition=0 offset=4 sourceTopic=pingbell.health-check.completed payloadType=HealthCheckCompletedEvent messageKey=10 status=REPROCESSABLE reason=HealthCheckCompleted can be retried as a dry-run decision
```

### 16.5 운영 로그만 사용하는 범위와 한계

운영 로그만으로 충분한 범위:

- 특정 DLQ record의 topic / partition / offset 식별
- payload type과 주요 id 확인
- record read 가능 여부 확인
- dry-run 결과와 실제 reprocess 실행 여부 확인
- batch dry-run의 재처리 후보 수 집계

운영 로그만으로 부족한 범위:

- 운영자별 실행자 추적
- 같은 record의 장기 재처리 시도 횟수 집계
- reprocess 승인 이력 저장
- UI에서 DLQ 처리 상태 조회
- command 로그가 보관 기간을 지나 삭제된 뒤의 사후 감사
- DB 보정 작업과 DLQ reprocess 사이의 원자적 audit trail 보장

따라서 다음 조건이 생기면 DLQ table 또는 별도 audit table 도입을 다시 검토한다.

- batch reprocess를 실제로 제공한다.
- 운영자 권한별 승인/실행 이력이 필요하다.
- DLQ 처리 상태를 UI/API로 조회해야 한다.
- 같은 record에 대한 여러 번의 dry-run/reprocess 시도를 장기간 보관해야 한다.
- 장애 사후 분석을 위해 로그 보관 기간보다 긴 audit trail이 필요하다.

## 17. 운영자가 확인해야 할 상태

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

## 18. 현재 구조를 유지하는 이유

지금은 별도 Worker 앱과 DLQ 재처리 UI/API 없이 현재 단일 앱 구조를 유지한다.

이유:

- 현재 MVP 기능은 단일 Spring Boot 앱에서 동작한다.
- 알림 재시도와 수동 재전송은 이미 `NotificationHistory` 상태 기반으로 검증 가능하다.
- 별도 Worker 앱을 먼저 분리하면 장애 판정과 알림 정책보다 운영 복잡도가 커진다.
- DLQ 수동 재처리는 dry-run과 idempotency 검증 없이 구현하면 위험하다.
- 지금은 각 단계의 retryable / non-retryable 기준, secret 제외 기준, 수동 확인 절차를 먼저 고정하는 것이 더 중요하다.

## 19. 이번 문서에서 하지 않은 일

- DLQ table을 만들지 않았다.
- 별도 Worker 애플리케이션을 만들지 않았다.
- 자동 재처리 스케줄러를 만들지 않았다.
- DB schema를 변경하지 않았다.
- 운영 대시보드나 metric collector를 구현하지 않았다.
- batch 재처리 command를 만들지 않았다.
- DLQ command 로그를 DB table에 저장하지 않았다.

## 20. 다음 작업

다음 이슈는 `DLQ command reason 민감 정보 redaction 기준 적용`이다.

다음 이슈에서 다룰 내용:

- `reason`에 URL, email, webhook URL, token 후보 문자열이 섞이지 않도록 공통 redaction 기준을 적용한다.
- DLQ command 실패 로그와 dry-run reason을 redaction 대상에 포함한다.
- command 출력 형식은 유지하고 민감 정보만 마스킹한다.
- DLQ table, batch reprocess, 운영 UI는 구현하지 않는다.
