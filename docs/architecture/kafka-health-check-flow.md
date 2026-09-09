# Kafka Health Check Flow

## 1. 목적

Pingbell의 Kafka 도입은 기존 health check 기능을 한 번에 Worker 구조로 바꾸기 위한 것이 아니다.

현재 목표는 기존 단일 Spring Boot 앱의 동기 처리 흐름을 유지하면서, Scheduler가 due monitor에 대한 check 요청 이벤트를 Kafka로 발행할 수 있는 경로를 추가하는 것이다. 이 작업은 이후 Check Worker, Incident Detector, Notification Worker로 책임을 분리하기 위한 첫 단계다.

## 2. 현재 구현 상태

현재 Kafka로 구현된 범위는 producer까지다.

- Kafka broker 로컬 실행 환경
- Spring Kafka producer 설정
- `direct|kafka` dispatch mode
- `HealthCheckRequested` topic 생성 설정
- due monitor 조회 후 `HealthCheckRequestedEvent` 발행

아직 구현하지 않은 범위는 다음과 같다.

- Kafka consumer
- Kafka 메시지를 받아 실제 URL을 호출하는 Check Worker
- `HealthCheckCompleted` 이벤트 발행
- Incident Detector consumer
- Notification Worker consumer
- retry / DLQ
- 별도 Worker 애플리케이션 분리

## 3. 기존 direct 흐름

기본값은 `direct` 모드다.

```text
CheckScheduler
-> CheckDispatchService
-> DirectCheckDispatchService
-> CheckService.healthCheck(now)
-> due monitor 조회
-> URL 호출
-> CheckResult 저장
-> Incident open / resolve 판정
-> Notification 발송
-> Monitor nextCheckAt 갱신
```

이 흐름은 Kafka 없이 기존 MVP 기능을 그대로 수행한다.

설정값:

```env
PINGBELL_CHECK_DISPATCH_MODE=direct
```

또는:

```yaml
pingbell:
  check:
    dispatch-mode: direct
```

## 4. 현재 kafka 흐름

`kafka` 모드에서는 Scheduler가 직접 URL을 호출하지 않고 Kafka 이벤트만 발행한다.

```text
CheckScheduler
-> CheckDispatchService
-> KafkaCheckDispatchService
-> due monitor 조회
-> HealthCheckRequestedEvent 생성
-> HealthCheckRequestedProducer
-> Kafka topic 발행
```

현재 kafka mode에서 실제 URL 호출은 수행되지 않는다. consumer가 아직 없기 때문이다.

설정값:

```env
PINGBELL_CHECK_DISPATCH_MODE=kafka
```

또는:

```yaml
pingbell:
  check:
    dispatch-mode: kafka
```

## 5. Kafka를 사용하는 위치

### Scheduler dispatch

`CheckScheduler`는 5초마다 실행되고 현재 시각을 만든 뒤 `CheckDispatchService.dispatch(now)`를 호출한다.

`CheckDispatchService` 구현체는 설정값으로 결정된다.

| mode | 구현체 | 역할 |
| --- | --- | --- |
| `direct` | `DirectCheckDispatchService` | 기존 `CheckService.healthCheck(now)` 호출 |
| `kafka` | `KafkaCheckDispatchService` | due monitor 조회 후 Kafka 이벤트 발행 |

### Kafka producer

`HealthCheckRequestedProducer`가 `KafkaTemplate<String, HealthCheckRequestedEvent>`를 사용해 메시지를 발행한다.

```text
topic: pingbell.health-check.requested
key: monitorId 문자열
value: HealthCheckRequestedEvent JSON
```

key를 `monitorId`로 둔 이유는 같은 monitor의 check 요청이 같은 partition으로 들어가도록 만들기 위한 기반이다. 현재 topic partition은 1개지만, 추후 partition을 늘릴 때 monitor 단위 순서를 유지하는 데 유리하다.

## 6. Topic 설정

topic 이름은 설정으로 분리되어 있다.

```env
PINGBELL_KAFKA_TOPIC_HEALTH_CHECK_REQUESTED=pingbell.health-check.requested
```

```yaml
pingbell:
  check:
    kafka:
      topic:
        health-check-requested: pingbell.health-check.requested
```

`KafkaTopicConfig`는 `kafka` dispatch mode에서만 활성화된다.

현재 topic bean 설정:

```text
name: pingbell.health-check.requested
partitions: 1
replicas: 1
```

로컬 단일 broker 환경이므로 replica는 1이다.

## 7. Event payload

현재 발행하는 이벤트 타입은 `HealthCheckRequestedEvent`다.

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

필드 의미:

| 필드 | 의미 |
| --- | --- |
| `eventId` | 이벤트 추적과 중복 처리 기준으로 사용할 UUID |
| `occurredAt` | 이벤트가 생성된 시각 |
| `monitorId` | check 대상 monitor id |
| `memberId` | monitor 소유 member id |
| `timeoutMillis` | check timeout 값 |
| `intervalSeconds` | check 주기 |
| `scheduledAt` | 원래 check가 예정되어 있던 `Monitor.nextCheckAt` |
| `requestedBy` | 요청 출처, 현재는 `SCHEDULER` |

payload에 넣지 않는 값:

- monitor URL 원문
- member email
- notification channel target
- webhook URL
- secret
- token

이렇게 제한한 이유는 Kafka 메시지가 여러 consumer, log, 운영 도구를 거칠 수 있기 때문이다. 민감 정보나 원문 URL은 이벤트에 싣지 않고, 다음 consumer 단계에서 DB를 다시 조회하는 방식이 더 안전하다.

## 8. 로컬 Kafka 구성

`docker-compose.yml`에는 `apache/kafka:3.9.1` 단일 broker가 추가되어 있다.

```yaml
kafka:
  image: apache/kafka:3.9.1
  container_name: pingbell-kafka
  ports:
    - "${KAFKA_PORT:-9092}:9092"
```

현재 구성은 KRaft 모드다. 별도 Zookeeper 컨테이너를 두지 않는다.

주요 설정:

| 설정 | 값 | 설명 |
| --- | --- | --- |
| `KAFKA_PROCESS_ROLES` | `broker,controller` | 단일 노드가 broker와 controller 역할을 함께 수행 |
| `KAFKA_CONTROLLER_QUORUM_VOTERS` | `1@kafka:9093` | 단일 controller quorum |
| `KAFKA_LISTENERS` | `PLAINTEXT://:9092,CONTROLLER://:9093` | broker / controller listener |
| `KAFKA_ADVERTISED_LISTENERS` | `PLAINTEXT://localhost:${KAFKA_PORT:-9092}` | 로컬 앱에서 접속할 주소 |
| `KAFKA_AUTO_CREATE_TOPICS_ENABLE` | `true` | 로컬 개발 편의를 위해 topic 자동 생성 허용 |

## 9. Spring Kafka 설정

`application.yml`의 Kafka 설정:

```yaml
spring:
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
```

환경변수:

```env
KAFKA_PORT=9092
KAFKA_BOOTSTRAP_SERVERS=localhost:9092
PINGBELL_CHECK_DISPATCH_MODE=direct
PINGBELL_KAFKA_TOPIC_HEALTH_CHECK_REQUESTED=pingbell.health-check.requested
```

producer value는 `JsonSerializer`를 사용한다. 따라서 `HealthCheckRequestedEvent` record가 JSON payload로 Kafka에 들어간다.

## 10. Kafka 발행 실패 처리

`HealthCheckRequestedProducer`는 메시지 발행 후 `join()`으로 결과를 기다린다.

발행 실패 시:

```text
error log 기록
IllegalStateException 발생
```

현재 기준은 Kafka 발행 실패를 숨기지 않는 것이다.

이유:

- kafka mode에서는 이벤트 발행이 check 요청 생성 자체다.
- 발행 실패를 무시하면 due monitor가 실제로 check되지 않았는데 성공처럼 보일 수 있다.
- 다음 단계에서 retry, outbox, DLQ를 붙이기 전까지는 실패를 명확히 드러내는 편이 안전하다.

## 11. 실행 방법

로컬 인프라 실행:

```powershell
docker compose up -d
```

Kafka만 별도로 실행:

```powershell
docker compose up -d kafka
```

기본 direct mode 실행:

```powershell
.\gradlew.bat bootRun
```

kafka mode 실행:

```powershell
.\gradlew.bat bootRun --args="--pingbell.check.dispatch-mode=kafka"
```

Kafka 상태 확인:

```powershell
docker ps --filter name=pingbell-kafka
docker logs --tail 30 pingbell-kafka
```

## 12. 현재 kafka mode의 주의점

현재 kafka mode는 이벤트 발행 검증용이다.

`PINGBELL_CHECK_DISPATCH_MODE=kafka`로 실행하면 Scheduler가 URL을 직접 호출하지 않는다. consumer가 아직 없기 때문에 다음 작업 전까지는 CheckResult 저장, incident 판정, notification 발송이 이어지지 않는다.

운영 기능을 확인하거나 기존 MVP 기능을 테스트할 때는 `direct` mode를 사용해야 한다.

## 13. 다음 구현 흐름

다음 단계는 `HealthCheckRequested` consumer 구현이다.

목표 흐름:

```text
CheckScheduler
-> HealthCheckRequested 발행
-> Check Worker consumer
-> monitorId로 Monitor DB 재조회
-> PAUSED / deleted / 이미 처리된 scheduledAt skip
-> URL 호출
-> CheckResult 저장
-> Monitor nextCheckAt 갱신
-> HealthCheckCompleted 발행
```

consumer 구현 시 중요한 기준:

- 이벤트 payload의 URL을 사용하지 않는다.
- DB에서 monitor 최신 상태를 다시 조회한다.
- `scheduledAt` 기준으로 중복 또는 오래된 요청을 skip할 수 있어야 한다.
- 기존 direct mode는 계속 유지한다.

## 14. 관련 코드

- `src/main/java/com/monit/pingbell/check/scheduler/CheckScheduler.java`
- `src/main/java/com/monit/pingbell/check/scheduler/dispatch/CheckDispatchService.java`
- `src/main/java/com/monit/pingbell/check/scheduler/dispatch/DirectCheckDispatchService.java`
- `src/main/java/com/monit/pingbell/check/scheduler/dispatch/KafkaCheckDispatchService.java`
- `src/main/java/com/monit/pingbell/check/scheduler/event/HealthCheckRequestedEvent.java`
- `src/main/java/com/monit/pingbell/check/scheduler/event/HealthCheckRequestedProducer.java`
- `src/main/java/com/monit/pingbell/check/scheduler/event/KafkaTopicConfig.java`
- `src/main/resources/application.yml`
- `docker-compose.yml`

## Update 2026-07-01: Consumer Retry / DLQ

Kafka mode now includes in-app consumers for `HealthCheckRequested`,
`HealthCheckCompleted`, and `NotificationRequested`. The same Spring Boot app
performs URL checks, incident detection, and notification delivery through
Kafka topics when `PINGBELL_CHECK_DISPATCH_MODE` is `kafka`.

Consumer failures use fixed backoff retry. Retry-exhausted messages and
non-retryable failures are published to source-topic `.dlq` topics.

Current DLQ topics:

```text
pingbell.health-check.requested.dlq
pingbell.health-check.completed.dlq
pingbell.notification.requested.dlq
```

A separate Worker application and DLQ reprocessing UI/API are still out of
scope.
