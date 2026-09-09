# Kafka Class Flow

작성일: 2026-07-01

이 문서는 Pingbell의 Kafka mode가 어떤 클래스 순서로 동작하는지 상황별로 정리한다.

## 1. 전제

Kafka mode는 다음 설정일 때 활성화된다.

```env
PINGBELL_CHECK_DISPATCH_MODE=kafka
```

이 설정이 켜지면 `@ConditionalOnProperty(name = "pingbell.check.dispatch-mode", havingValue = "kafka")`가 붙은 Kafka 전용 클래스들이 등록된다.

주요 topic:

```text
pingbell.health-check.requested
pingbell.health-check.completed
pingbell.notification.requested
pingbell.health-check.requested.dlq
pingbell.health-check.completed.dlq
pingbell.notification.requested.dlq
```

## 2. 애플리케이션 시작 시 Kafka 구성

1. `KafkaTopicConfig`
2. `healthCheckRequestedTopic(...)`
3. `healthCheckCompletedTopic(...)`
4. `notificationRequestedTopic(...)`
5. `healthCheckRequestedDlqTopic(...)`
6. `healthCheckCompletedDlqTopic(...)`
7. `notificationRequestedDlqTopic(...)`

역할:

- 일반 topic 3개를 생성한다.
- 각 일반 topic 옆에 `.dlq` topic 3개를 생성한다.
- topic 이름은 `application.yml`의 `pingbell.check.kafka.topic.*` 설정을 따른다.

consumer 실패 처리 구성:

1. `KafkaConsumerErrorHandlerConfig`
2. `kafkaConsumerErrorHandler(...)`
3. `KafkaConsumerFailurePolicy`
4. `DefaultErrorHandler`
5. `DeadLetterPublishingRecoverer`

역할:

- consumer에서 예외가 다시 던져지면 `DefaultErrorHandler`가 처리한다.
- retryable 예외는 fixed backoff로 재시도한다.
- non-retryable 예외 또는 retry exhausted 메시지는 `.dlq` topic으로 보낸다.

## 3. Scheduler가 due monitor를 찾는 흐름

상황: `kafka` mode에서 5초 주기 scheduler가 실행된다.

순서:

1. `CheckScheduler.check()`
2. `KafkaCheckDispatchService.dispatch(now)`
3. `MonitorRepository.findAllByStatusInAndDeletedAtIsNullAndNextCheckAtLessThanEqual(...)`
4. `HealthCheckRequestedEvent.from(monitor, now)`
5. `HealthCheckRequestedProducer.publish(event)`
6. `KafkaTemplate.send(topicName, monitorId, event).join()`
7. Kafka topic `pingbell.health-check.requested`

이 단계에서는 URL을 직접 호출하지 않는다.

`KafkaCheckDispatchService`는 due monitor를 조회하고 `HealthCheckRequestedEvent`만 발행한다.

payload에 들어가는 값:

```text
eventId
occurredAt
monitorId
memberId
timeoutMillis
intervalSeconds
scheduledAt
requestedBy
```

payload에 넣지 않는 값:

```text
monitor URL
email
notification target
secret
```

## 4. HealthCheckRequested 소비 후 실제 URL 체크

상황: `pingbell.health-check.requested` topic에 메시지가 들어왔다.

순서:

1. Kafka topic `pingbell.health-check.requested`
2. `HealthCheckRequestedConsumer.consume(event)`
3. `CheckService.handleRequestedCheck(event, now)`
4. `MonitorRepository.findByIdAndDeletedAtIsNull(event.monitorId())`
5. `CheckService.checkMonitor(monitor, now)`
6. `HealthCheckClient.check(monitor.getUrl(), monitor.getTimeoutMillis())`
7. `CheckResultRepository.save(checkResult)`
8. `PingbellMetrics.recordHealthCheck(...)`
9. `Monitor.updateNextCheckedAt(...)`
10. `HealthCheckCompletedEvent.from(event, checkResult, now)`
11. `HealthCheckCompletedProducer.publish(completedEvent)`
12. Kafka topic `pingbell.health-check.completed`

이 단계에서 실제 URL 호출과 `CheckResult` 저장이 일어난다.

`HealthCheckRequestedConsumer`는 DB에서 monitor를 다시 조회한다. Kafka payload에 URL을 넣지 않기 때문에, 실제 호출에 필요한 URL은 DB의 `Monitor`에서 가져온다.

## 5. HealthCheckRequested 소비 중 skip되는 경우

상황: 요청 이벤트가 왔지만 실제 체크를 하지 않아야 한다.

### 5.1 Monitor가 없는 경우

순서:

1. `HealthCheckRequestedConsumer.consume(event)`
2. `CheckService.handleRequestedCheck(event, now)`
3. `MonitorRepository.findByIdAndDeletedAtIsNull(event.monitorId())`
4. monitor 없음
5. info log 기록
6. `Optional.empty()` 반환
7. `HealthCheckCompletedProducer.publish(...)` 호출 안 함

DLQ로 가지 않는다. 삭제된 monitor에 대한 오래된 이벤트는 정상 skip으로 본다.

### 5.2 Monitor 상태가 check 대상이 아닌 경우

대상 상태:

```text
ACTIVE
DOWN
```

그 외 상태면 흐름은 다음과 같다.

1. `CheckService.handleRequestedCheck(event, now)`
2. monitor 상태 확인
3. check 불가능 상태면 info log 기록
4. `Optional.empty()` 반환
5. completed event 발행 안 함

### 5.3 이미 처리된 오래된 요청인 경우

기준:

```java
monitor.getNextCheckAt().isAfter(event.scheduledAt())
```

순서:

1. `CheckService.handleRequestedCheck(event, now)`
2. `event.scheduledAt()`과 `monitor.nextCheckAt` 비교
3. 이미 더 최신 check 일정으로 갱신되어 있으면 skip
4. completed event 발행 안 함

## 6. HealthCheckCompleted 소비 후 장애 판정

상황: URL 체크 결과가 저장되고 `pingbell.health-check.completed` topic에 메시지가 들어왔다.

순서:

1. Kafka topic `pingbell.health-check.completed`
2. `HealthCheckCompletedConsumer.consume(event)`
3. `IncidentDetectionService.detectFromCompletedEvent(event, now)`
4. `CheckResultRepository.findById(event.checkResultId())`
5. event의 `monitorId`, `memberId`와 DB의 `CheckResult.monitor` 일치 여부 확인
6. `IncidentDetectionService.detectOnce(checkResult, now)`
7. `IncidentDetectionProcessedCheckResultRepository.existsByCheckResultId(...)`
8. `IncidentDetectionProcessedCheckResultRepository.saveAndFlush(...)`
9. `IncidentDetectionService.applyDetection(checkResult, now)`

`IncidentDetectionProcessedCheckResult`는 같은 `CheckResult`에 대해 장애 판정이 중복 실행되지 않도록 막는 idempotency 경계다.

## 7. HealthCheckCompleted가 중복일 때

상황: 같은 `checkResultId`를 가진 completed event가 중복 소비된다.

순서:

1. `HealthCheckCompletedConsumer.consume(event)`
2. `IncidentDetectionService.detectFromCompletedEvent(event, now)`
3. `IncidentDetectionService.detectOnce(checkResult, now)`
4. `processedRepository.existsByCheckResultId(checkResult.getId())`
5. 이미 처리됨
6. info log 기록
7. 장애 판정 재실행 안 함
8. 알림 발행 안 함

동시에 중복 이벤트가 들어와 unique constraint 충돌이 나도 `DataIntegrityViolationException`을 잡고 skip한다.

## 8. 장애 발생 판정 후 알림 요청 발행

상황: check result가 실패이고 장애 발생 조건을 만족한다.

순서:

1. `IncidentDetectionService.applyDetection(checkResult, now)`
2. `Monitor.recordFailure()`
3. `Monitor.canOpenIncident()`
4. `IncidentRepository.existsByMonitorAndStatus(monitor, OPEN)`
5. `IncidentRepository.save(new Incident(...))`
6. `Monitor.markDown()`
7. `PingbellMetrics.recordIncidentOpened()`
8. `IncidentDetectionService.notifyIncidentOpened(incident, now)`
9. `KafkaNotificationDispatchService.dispatch(incident, INCIDENT_OPEN, now)`
10. `NotificationRequestedEvent.from(incident, INCIDENT_OPEN, now)`
11. transaction active 여부 확인
12. transaction commit 후 `NotificationRequestedProducer.publish(event)`
13. Kafka topic `pingbell.notification.requested`

`KafkaNotificationDispatchService`는 transaction이 활성화되어 있으면 `TransactionSynchronization.afterCommit()`에서 Kafka event를 발행한다.

이유:

- `Incident` 저장 transaction이 commit되기 전에 notification consumer가 DB를 조회하면 아직 incident를 못 볼 수 있다.
- 그래서 commit 이후에 `NotificationRequestedEvent`를 발행한다.

## 9. 장애 복구 판정 후 알림 요청 발행

상황: check result가 성공이고 복구 조건을 만족한다.

순서:

1. `IncidentDetectionService.applyDetection(checkResult, now)`
2. `Monitor.recordSuccess()`
3. `Monitor.canRecover()`
4. `IncidentRepository.findByMonitorAndStatus(monitor, OPEN)`
5. `Incident.resolve(now)`
6. `Monitor.recover()`
7. `PingbellMetrics.recordIncidentResolved()`
8. `IncidentDetectionService.notifyIncidentResolved(incident, now)`
9. `KafkaNotificationDispatchService.dispatch(incident, INCIDENT_RESOLVED, now)`
10. `NotificationRequestedEvent.from(incident, INCIDENT_RESOLVED, now)`
11. transaction commit 후 `NotificationRequestedProducer.publish(event)`
12. Kafka topic `pingbell.notification.requested`

## 10. NotificationRequested 소비 후 실제 알림 발송

상황: `pingbell.notification.requested` topic에 메시지가 들어왔다.

순서:

1. Kafka topic `pingbell.notification.requested`
2. `NotificationRequestedConsumer.consume(event)`
3. `NotificationWorkerService.handle(event)`
4. `IncidentRepository.findById(event.incidentId())`
5. `NotificationService.notifyIncidentOpened(...)` 또는 `NotificationService.notifyIncidentResolved(...)`
6. `NotificationChannelRepository.findAllByMemberAndEnabledTrue(...)`
7. 필요하면 기본 EMAIL `NotificationChannel` 생성
8. `NotificationHistoryRepository.existsByIncidentAndChannelAndNotificationType(...)`
9. `NotificationHistoryRepository.save(new NotificationHistory(...))`
10. `NotificationSender.send(channel, message)`
11. 성공 시 `NotificationHistory.markSent(now)`
12. 실패 시 `NotificationFailureClassifier.classify(e)`
13. retryable이면 `NotificationHistory.markRetryPending(...)`
14. non-retryable이면 `NotificationHistory.markFailed(...)`
15. `PingbellMetrics.recordNotificationDelivery(...)`

실제 email/slack/discord/webhook target은 Kafka payload에서 가져오지 않는다.

`NotificationWorkerService`가 `incidentId`로 DB에서 `Incident`를 다시 조회하고, `NotificationService`가 DB의 `NotificationChannel`을 조회한다.

## 11. NotificationRequested가 중복일 때

상황: 같은 incident, channel, notification type에 대해 notification event가 중복 소비된다.

순서:

1. `NotificationRequestedConsumer.consume(event)`
2. `NotificationWorkerService.handle(event)`
3. `NotificationService.notify(...)`
4. `NotificationHistoryRepository.existsByIncidentAndChannelAndNotificationType(...)`
5. 이미 이력이 있으면 `continue`
6. `NotificationSender.send(...)` 호출 안 함

중복 발송 방지는 Kafka offset이 아니라 DB의 `NotificationHistory` 조회 기준으로 한다.

기준:

```text
incident
channel
notificationType
```

## 12. Producer 발행 실패

Producer 클래스:

```text
HealthCheckRequestedProducer
HealthCheckCompletedProducer
NotificationRequestedProducer
```

공통 순서:

1. `producer.publish(event)`
2. `KafkaTemplate.send(...).join()`
3. 발행 실패
4. error log 기록
5. `IllegalStateException` 발생

각 producer는 `join()`으로 발행 결과를 기다린다. 실패를 숨기지 않고 호출자에게 예외를 다시 올린다.

주의:

- producer 발행 실패는 consumer DLQ와 다르다.
- DLQ는 consumer가 메시지를 받은 뒤 처리에 실패했을 때의 경계다.
- producer가 broker에 메시지를 보내지 못하면 애초에 source topic에 record가 없으므로 DLQ로 이동할 record도 없다.

## 13. Consumer 처리 실패와 retry/DLQ

Consumer 클래스:

```text
HealthCheckRequestedConsumer
HealthCheckCompletedConsumer
NotificationRequestedConsumer
```

공통 순서:

1. consumer의 `consume(event)` 실행
2. 내부 service 호출 중 예외 발생
3. consumer가 error log 기록
4. 같은 예외를 다시 throw
5. `KafkaConsumerErrorHandlerConfig.kafkaConsumerErrorHandler(...)`
6. `DefaultErrorHandler`
7. `KafkaConsumerFailurePolicy` 기준으로 retryable 여부 판단

### 13.1 Retryable 실패

순서:

1. consumer 예외 발생
2. `DefaultErrorHandler`
3. `FixedBackOff(backoffIntervalMs, maxRetries)`
4. 같은 record 재처리
5. 성공하면 offset 처리
6. 계속 실패해 retry를 소진하면 `DeadLetterPublishingRecoverer`
7. source topic + `.dlq` topic으로 record 발행

기본값:

```env
PINGBELL_KAFKA_CONSUMER_RETRY_BACKOFF_INTERVAL_MS=1000
PINGBELL_KAFKA_CONSUMER_RETRY_MAX_RETRIES=2
```

### 13.2 Non-retryable 실패

현재 non-retryable 예외:

```text
IllegalArgumentException
ClassCastException
NoSuchElementException
```

순서:

1. consumer 예외 발생
2. `DefaultErrorHandler`
3. `KafkaConsumerFailurePolicy.nonRetryableExceptions()`
4. retry 없이 `DeadLetterPublishingRecoverer`
5. source topic + `.dlq` topic으로 record 발행

예시:

- `HealthCheckCompletedEvent`의 `checkResultId`가 DB에 없음
- event의 `monitorId/memberId`와 DB의 `CheckResult`가 불일치
- `NotificationRequestedEvent`의 `incidentId`가 DB에 없음

## 14. DLQ로 이동할 때

순서:

1. `DeadLetterPublishingRecoverer`
2. `KafkaConsumerFailurePolicy.dlqTopic(record.topic())`
3. `new TopicPartition(sourceTopic + ".dlq", record.partition())`
4. DLQ topic에 원본 record value 발행

DLQ header:

```text
pingbell-dlq-original-topic
pingbell-dlq-original-partition
pingbell-dlq-original-offset
pingbell-dlq-exception
```

DLQ header에 넣지 않는 값:

```text
exception stack trace
exception message
monitor URL
notification target
webhook URL
email
secret
```

## 15. direct mode와 차이

설정:

```env
PINGBELL_CHECK_DISPATCH_MODE=direct
```

direct mode 순서:

1. `CheckScheduler.check()`
2. `DirectCheckDispatchService.dispatch(now)`
3. `CheckService.healthCheck(now)`
4. `HealthCheckClient.check(...)`
5. `CheckResultRepository.save(...)`
6. `IncidentDetectionService.detect(checkResult, now)`
7. `DirectNotificationDispatchService.dispatch(...)`
8. `NotificationService.notifyIncidentOpened(...)` 또는 `notifyIncidentResolved(...)`

direct mode에서는 다음 클래스가 사용되지 않는다.

```text
KafkaCheckDispatchService
HealthCheckRequestedProducer
HealthCheckRequestedConsumer
HealthCheckCompletedProducer
HealthCheckCompletedConsumer
KafkaNotificationDispatchService
NotificationRequestedProducer
NotificationRequestedConsumer
KafkaConsumerErrorHandlerConfig
```

## 16. 전체 정상 흐름 요약

장애 발생까지 이어지는 kafka mode 정상 흐름:

```text
CheckScheduler
-> KafkaCheckDispatchService
-> HealthCheckRequestedProducer
-> pingbell.health-check.requested
-> HealthCheckRequestedConsumer
-> CheckService.handleRequestedCheck
-> HealthCheckClient
-> CheckResultRepository
-> HealthCheckCompletedProducer
-> pingbell.health-check.completed
-> HealthCheckCompletedConsumer
-> IncidentDetectionService.detectFromCompletedEvent
-> IncidentRepository
-> KafkaNotificationDispatchService
-> NotificationRequestedProducer
-> pingbell.notification.requested
-> NotificationRequestedConsumer
-> NotificationWorkerService
-> NotificationService
-> NotificationSender
-> NotificationHistory
```

복구 알림도 같은 흐름을 사용하되 notification type이 `INCIDENT_RESOLVED`로 바뀐다.
