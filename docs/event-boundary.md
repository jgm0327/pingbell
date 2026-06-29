# Pingbell Event Boundary

작성일: 2026-06-29

## 1. 목적

이 문서는 Kafka, 별도 Worker, DLQ를 바로 구현하기 전에 현재 단일 Spring Boot 애플리케이션 안의 이벤트 경계를 정리한다.

현재 코드는 Scheduler와 Service가 같은 프로세스 안에서 동기 호출로 이어진다. 이 구조를 유지하면서도 나중에 Check Worker, Incident Detector, Notification Worker로 분리할 수 있도록 책임 단위와 실패 처리 기준을 먼저 고정한다.

## 2. 현재 원칙

- 지금은 Kafka를 도입하지 않는다.
- 지금은 별도 worker 애플리케이션을 만들지 않는다.
- 현재 기능은 단일 Spring Boot 앱 안에서 계속 동작해야 한다.
- 이벤트 이름은 미래 분리를 위한 설계 기준이며, 현재 코드에 message producer / consumer를 추가하지 않는다.
- 알림 실패는 CheckResult 저장과 Incident 판정을 막지 않아야 한다.
- 외부 URL 호출 실패는 Pingbell API 실패가 아니라 개별 monitor의 check 실패로 기록한다.

## 3. 현재 동기 실행 흐름

### 3.1 Health Check 흐름

현재 실행 경로:

```text
CheckScheduler
-> CheckService.healthCheck(now)
-> MonitorRepository.findAllByStatusInAndDeletedAtIsNullAndNextCheckAtLessThanEqual(...)
-> HealthCheckClient.check(url, timeoutMillis)
-> CheckResultRepository.save(checkResult)
-> Monitor.recordSuccess() 또는 Monitor.recordFailure()
-> IncidentRepository 조회 / 저장 / resolve
-> NotificationService.notifyIncidentOpened(...) 또는 notifyIncidentResolved(...)
-> Monitor.updateNextCheckedAt(...)
```

현재 특징:

- `CheckScheduler`는 5초마다 실행된다.
- `ACTIVE`, `DOWN` 상태이고 `nextCheckAt <= now`인 monitor가 체크 대상이다.
- HTTP 4xx / 5xx는 `HTTP_ERROR`로 기록한다.
- timeout 예외는 `TIMEOUT`으로 기록한다.
- 응답 시간이 `timeoutMillis`를 초과하면 `SLOW_RESPONSE`로 기록한다.
- 그 외 호출 실패는 `FAILURE`로 기록한다.
- `SUCCESS`가 아닌 check 결과는 실패 카운트를 증가시킨다.
- `failureThreshold`에 도달하면 open incident를 생성하고 monitor를 `DOWN`으로 변경한다.
- `DOWN` 상태에서 `recoveryThreshold`만큼 연속 성공하면 incident를 resolve하고 monitor를 `ACTIVE`로 복구한다.

### 3.2 Notification 발송 흐름

현재 실행 경로:

```text
CheckService
-> NotificationService.notifyIncidentOpened(...) 또는 notifyIncidentResolved(...)
-> NotificationChannelRepository.findAllByMemberAndEnabledTrue(...)
-> NotificationHistoryRepository.existsByIncidentAndChannelAndNotificationType(...)
-> NotificationHistoryRepository.save(new NotificationHistory(...))
-> NotificationSender.send(...)
-> NotificationHistory.markSent(...) 또는 markRetryPending(...) 또는 markFailed(...)
```

현재 특징:

- `NotificationService`는 `REQUIRES_NEW` 트랜잭션으로 실행된다.
- 같은 incident, channel, notification type에 대해서는 중복 발송하지 않는다.
- 활성화된 알림 채널이 없고 기존 채널도 없으면 기본 EMAIL 채널을 생성한다.
- 발송 성공은 `SENT`로 기록한다.
- retryable 실패는 `RETRY_PENDING`으로 기록하고 `nextRetryAt`을 설정한다.
- non-retryable 실패는 `FAILED`로 기록한다.
- `CheckService`는 알림 예외를 내부에서 무시해 check 결과 저장과 incident 판정이 알림 실패 때문에 롤백되지 않게 한다.

### 3.3 Notification Retry 흐름

현재 실행 경로:

```text
NotificationRetryScheduler
-> NotificationRetryService.retryDueHistories(now)
-> NotificationHistoryRepository.findRetryDueHistories(now)
-> NotificationSender.send(...)
-> NotificationHistory.markSent(...) 또는 markRetryPending(...) 또는 markFailed(...)
```

현재 특징:

- `NotificationRetryScheduler`는 10초마다 실행된다.
- `RETRY_PENDING`이고 `nextRetryAt <= now`인 이력을 재시도한다.
- 채널이 비활성화되면 최종 `FAILED`로 처리한다.
- retryable 실패이고 최대 재시도 횟수에 도달하지 않았으면 다시 `RETRY_PENDING`으로 둔다.
- 최대 재시도 횟수를 넘거나 non-retryable 실패면 `FAILED`로 처리한다.

### 3.4 Manual Resend 흐름

현재 실행 경로:

```text
NotificationHistoryController
-> NotificationHistoryResendService.resend(memberId, historyId, now)
-> NotificationHistoryRepository.findByIdAndChannelMemberId(...)
-> NotificationHistory.manualResend(...)
-> NotificationSender.send(...)
-> NotificationHistory.markSent(...) 또는 markRetryPending(...) 또는 markFailed(...)
```

현재 특징:

- `FAILED` 상태의 알림 이력만 수동 재전송할 수 있다.
- 채널이 비활성화되어 있으면 재전송하지 않는다.
- 원본 이력을 덮어쓰지 않고 새 `NotificationHistory`를 만든다.
- 새 이력에는 `manualResend=true`, `resendOfHistoryId=원본 이력 id`를 기록한다.

## 4. 미래 이벤트 후보

이벤트는 현재 코드에 바로 추가하지 않는다. 아래 목록은 Worker 분리 시 사용할 논리적 경계다.

| 이벤트 | 목적 | Producer 후보 | Consumer 후보 |
| --- | --- | --- | --- |
| `HealthCheckRequested` | due monitor에 대한 체크 요청 생성 | Check Scheduler | Check Worker |
| `HealthCheckCompleted` | URL 호출 결과와 측정값 전달 | Check Worker | Incident Detector |
| `IncidentOpened` | 장애 발생 사실 전달 | Incident Detector | Notification Worker |
| `IncidentResolved` | 장애 복구 사실 전달 | Incident Detector | Notification Worker |
| `NotificationRequested` | 특정 채널로 알림 발송 요청 | Notification Worker 또는 Incident Detector | Notification Sender Worker |
| `NotificationSent` | 알림 발송 성공 결과 기록 | Notification Sender Worker | Notification History Projector |
| `NotificationFailed` | 알림 발송 실패 결과 기록 | Notification Sender Worker | Retry / DLQ Handler |

## 5. 이벤트 Payload 초안

### 5.1 HealthCheckRequested

목적: 체크 대상 monitor와 호출 조건을 worker로 전달한다.

```text
eventId
occurredAt
monitorId
memberId
url
timeoutMillis
intervalSeconds
failureThreshold
recoveryThreshold
scheduledAt
```

주의:

- `url`은 체크 실행에 필요하지만 외부로 노출하지 않는다.
- monitor 설정이 바뀔 수 있으므로 worker가 실행 시점에 DB에서 최신 monitor를 다시 조회하는 방식도 고려한다.

### 5.2 HealthCheckCompleted

목적: URL 호출 결과를 incident 판정 단계로 전달한다.

```text
eventId
occurredAt
monitorId
checkResultId
status
httpStatus
responseTimeMs
errorMessage
checkedAt
```

주의:

- CheckResult 저장 후 `checkResultId`를 포함하는 방식을 우선 고려한다.
- 이벤트 payload에 모든 상세값을 넣더라도 DB의 CheckResult를 source of truth로 둔다.

### 5.3 IncidentOpened

목적: 새 open incident 생성을 알림 단계로 전달한다.

```text
eventId
occurredAt
incidentId
monitorId
memberId
startedAt
lastErrorMessage
```

주의:

- 이미 open incident가 있으면 새 이벤트를 만들지 않는다.
- 중복 이벤트가 들어와도 Notification 쪽에서 incident, channel, notification type 기준으로 중복 발송을 막아야 한다.

### 5.4 IncidentResolved

목적: incident 복구를 알림 단계로 전달한다.

```text
eventId
occurredAt
incidentId
monitorId
memberId
startedAt
resolvedAt
```

주의:

- 복구 이벤트는 `recoveryThreshold` 충족 이후 한 번만 발생해야 한다.
- monitor 상태 변경과 incident resolve가 먼저 커밋된 뒤 알림 요청이 처리되는 구조가 적합하다.

### 5.5 NotificationRequested

목적: 특정 incident와 notification type에 대해 채널별 발송을 요청한다.

```text
eventId
occurredAt
incidentId
memberId
notificationType
channelId
channelType
```

주의:

- webhook target이나 이메일 주소 원문은 이벤트에 넣지 않는 편이 안전하다.
- sender는 channelId로 DB에서 target을 조회한다.

### 5.6 NotificationSent

목적: 발송 성공 결과를 이력에 반영한다.

```text
eventId
occurredAt
notificationHistoryId
incidentId
channelId
notificationType
sentAt
```

주의:

- 현재 구조에서는 `NotificationHistory.markSent(...)`가 이 역할을 한다.

### 5.7 NotificationFailed

목적: 발송 실패 결과와 재시도 가능 여부를 이력에 반영한다.

```text
eventId
occurredAt
notificationHistoryId
incidentId
channelId
notificationType
errorMessage
retryable
retryCount
maxRetryCount
nextRetryAt
```

주의:

- retryable이면 `RETRY_PENDING`으로 전이한다.
- non-retryable이거나 최대 재시도 횟수를 넘으면 `FAILED`로 전이한다.
- 나중에 DLQ를 도입하면 최종 실패 이력을 DLQ 처리 대상으로 삼는다.

## 6. 지금 동기로 유지할 부분

다음 부분은 현재 단일 앱 단계에서 동기로 유지한다.

- due monitor 조회
- URL 호출
- CheckResult 저장
- Monitor failure / recovery count 갱신
- Incident open / resolve 판정
- NotificationHistory 생성
- NotificationRetryScheduler 기반 재시도
- 수동 재전송 API

이유:

- MVP와 현재 기능은 단일 앱으로 이미 검증되어 있다.
- Kafka를 먼저 붙이면 장애 판정과 알림 재시도보다 운영 복잡도가 먼저 커진다.
- 현재 트랜잭션 경계만으로도 알림 실패가 check 저장을 막지 않는 구조가 되어 있다.

## 7. 나중에 비동기로 분리할 부분

Worker 분리 후보는 다음 순서가 적절하다.

1. Check Worker
   - Scheduler는 `HealthCheckRequested`만 만든다.
   - Check Worker가 URL 호출과 CheckResult 저장을 맡는다.
2. Incident Detector
   - `HealthCheckCompleted`를 받아 Monitor count 갱신과 Incident open / resolve를 맡는다.
3. Notification Worker
   - `IncidentOpened`, `IncidentResolved`를 받아 채널별 NotificationHistory 생성과 발송을 맡는다.
4. Retry / DLQ Handler
   - `NotificationFailed`와 `RETRY_PENDING` 이력을 기준으로 재시도와 최종 실패 처리를 맡는다.

## 8. 실패 처리 기준

### 8.1 Check 실패

- 외부 URL 호출 실패는 Pingbell 서버 실패로 보지 않는다.
- 실패는 `CheckResult`로 저장한다.
- `SUCCESS`가 아닌 상태는 monitor의 failure count를 증가시킨다.
- 실패가 연속 기준에 도달하면 Incident를 open한다.

### 8.2 Incident 판정 실패

- 현재 코드에서는 incident 판정이 check 트랜잭션 안에 있다.
- 나중에 분리할 경우 `HealthCheckCompleted` 처리 중 실패하면 같은 이벤트를 재처리할 수 있어야 한다.
- 중복 open 방지를 위해 monitor별 open incident 존재 여부를 반드시 확인한다.
- resolve 처리도 이미 resolved 상태인지 확인 가능한 idempotent 구조가 필요하다.

### 8.3 Notification 발송 실패

- 알림 실패는 check 결과 저장과 incident 판정을 롤백시키지 않는다.
- 발송 결과는 `NotificationHistory`에 남긴다.
- retryable 실패는 `RETRY_PENDING`으로 둔다.
- non-retryable 실패는 `FAILED`로 둔다.
- 같은 incident, channel, notification type은 중복 발송하지 않는다.

### 8.4 Notification 재시도 실패

- 채널이 비활성화되어 있으면 `FAILED`로 종료한다.
- retryable 실패가 계속되면 최대 재시도 횟수 전까지 `RETRY_PENDING`을 유지한다.
- 최대 재시도 횟수를 넘으면 `FAILED`로 종료한다.
- DLQ 도입 시 `FAILED` 중 retry exhausted인 이력을 DLQ 후보로 삼는다.

## 9. 다음 이슈 분해

### Issue 1. Check Worker 분리 설계

목표:

- 현재 `CheckScheduler`와 `CheckService` 책임을 분리 후보 기준으로 정리한다.

완료 조건:

- `HealthCheckRequested`, `HealthCheckCompleted` 기준의 책임 분리가 문서화된다.
- CheckResult 저장 위치가 정리된다.
- monitor nextCheckAt 갱신 위치가 정리된다.
- Kafka 없이 현재 코드를 유지하는 기준이 함께 적힌다.

담당:

- PM Agent
- Backend Agent 검토 가능

### Issue 2. Incident Detector 분리 설계

목표:

- CheckResult 이후 monitor count와 incident open / resolve 책임을 분리 후보로 정리한다.

완료 조건:

- 중복 open 방지 기준이 정리된다.
- resolve idempotency 기준이 정리된다.
- monitor 상태 전이 책임이 정리된다.

담당:

- PM Agent
- Backend Agent 검토 가능

### Issue 3. Notification Worker 분리 설계

목표:

- incident 이벤트 이후 채널별 알림 발송 책임을 분리 후보로 정리한다.

완료 조건:

- `IncidentOpened`, `IncidentResolved`, `NotificationRequested` 흐름이 정리된다.
- 중복 발송 방지 기준이 정리된다.
- target 원문을 이벤트에 넣지 않는 보안 기준이 정리된다.

담당:

- PM Agent
- Backend Agent 검토 가능

### Issue 4. DLQ / 재처리 정책 설계

목표:

- check, incident, notification 단계별 최종 실패 처리 기준을 정리한다.

완료 조건:

- retryable / non-retryable 실패 기준이 정리된다.
- retry exhausted 이후 처리 기준이 정리된다.
- 수동 재처리 대상과 자동 재처리 대상을 구분한다.

담당:

- PM Agent
- Backend Agent 검토 가능

### Issue 5. 관측성 지표 목록 작성

목표:

- Worker 분리 전후로 필요한 최소 운영 지표를 정리한다.

완료 조건:

- check 성공 / 실패 / timeout / slow response count가 정리된다.
- incident open / resolved count가 정리된다.
- notification sent / failed / retry pending count가 정리된다.
- p95 / p99 응답 시간 지표 후보가 정리된다.

담당:

- PM Agent
- Infra Agent 검토 가능

## 10. 이번 문서에서 하지 않은 일

- Kafka 의존성을 추가하지 않았다.
- producer / consumer 코드를 작성하지 않았다.
- 별도 worker 애플리케이션을 만들지 않았다.
- DLQ topic 또는 table을 만들지 않았다.
- Kubernetes / MSA 구성으로 넘어가지 않았다.
