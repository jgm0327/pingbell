# Pingbell Incident Detector Design

작성일: 2026-06-29

## 1. 목적

이 문서는 `HealthCheckCompleted` 이후 monitor count 갱신, incident open / resolve 판정, monitor 상태 전이 책임을 미래 `Incident Detector` 분리 관점에서 정리한다.

이번 작업은 설계 문서 작업이다. Kafka, message producer, message consumer, 별도 Incident Detector 애플리케이션은 구현하지 않는다.

## 2. 현재 동기 구조

현재 incident 판정은 단일 Spring Boot 애플리케이션 안에서 `CheckService.healthCheck(now)` 트랜잭션에 포함되어 있다.

```text
CheckScheduler
-> CheckService.healthCheck(now)
-> due monitor 조회
-> URL 호출
-> CheckResult 저장
-> Monitor failure/recovery count 갱신
-> Incident open/resolve 판정
-> NotificationService 호출
-> Monitor nextCheckAt 갱신
```

현재 `CheckService.healthCheck`의 incident 관련 책임:

- `CheckResult`가 성공이면 `monitor.recordSuccess()`를 호출한다.
- `CheckResult`가 실패이면 `monitor.recordFailure()`를 호출한다.
- `DOWN` monitor의 연속 성공 횟수가 `recoveryThreshold` 이상이면 open incident를 조회해 resolve한다.
- 복구 시 `monitor.recover()`로 monitor 상태를 `ACTIVE`로 되돌린다.
- `ACTIVE` monitor의 연속 실패 횟수가 `failureThreshold` 이상이고 open incident가 없으면 새 incident를 생성한다.
- 장애 발생 시 `monitor.markDown()`으로 monitor 상태를 `DOWN`으로 바꾼다.
- incident open / resolve 이후 알림 서비스를 호출한다.
- 알림 실패는 try-catch로 격리해 check 결과 저장과 incident 판정을 롤백하지 않는다.

## 3. 미래 분리 목표

Incident Detector 분리 후 책임 경계는 다음과 같다.

| 구성 | 책임 |
| --- | --- |
| Check Scheduler | due monitor에 대한 `HealthCheckRequested` 생성 |
| Check Worker | URL 호출, `CheckResult` 저장, `HealthCheckCompleted` 생성 |
| Incident Detector | `HealthCheckCompleted` 처리, monitor count 갱신, incident open / resolve, 상태 전이 |
| Notification Worker | `IncidentOpened`, `IncidentResolved` 이후 채널별 알림 생성과 발송 |

이번 문서에서는 Incident Detector 경계까지만 다룬다. Notification Worker 상세 설계는 다음 이슈에서 다룬다.

## 4. HealthCheckCompleted 처리 기준

`HealthCheckCompleted`는 Check Worker가 URL 호출과 `CheckResult` 저장을 완료한 뒤 생성하는 이벤트다.

Incident Detector의 기본 처리 순서:

```text
HealthCheckCompleted consume
-> checkResultId 기준으로 CheckResult 조회
-> monitor 최신 상태 조회
-> monitor 처리 가능 여부 확인
-> CheckResult 성공/실패 판정
-> monitor count 갱신
-> incident open 또는 resolve 필요 여부 판단
-> monitor 상태 전이
-> IncidentOpened 또는 IncidentResolved 생성
```

처리 가능 조건:

- monitor가 존재해야 한다.
- monitor가 soft delete 상태가 아니어야 한다.
- monitor 상태가 `ACTIVE` 또는 `DOWN`이어야 한다.
- `CheckResult`가 해당 monitor의 결과여야 한다.

처리하지 않는 조건:

- monitor가 삭제되었다.
- monitor가 `PAUSED` 상태다.
- 같은 `checkResultId`가 이미 incident 판정에 반영되었다.
- `CheckResult`와 event payload의 monitor id가 다르다.

## 5. Source of Truth

Incident Detector는 이벤트 payload보다 DB의 `CheckResult`를 우선한다.

기준:

- `HealthCheckCompleted.checkResultId`로 `CheckResult`를 조회한다.
- status, http status, response time, error message는 DB 값을 기준으로 판단한다.
- 이벤트 payload는 라우팅과 추적용 보조 정보로 둔다.

이유:

- 이벤트 중복 전달이나 payload 불일치가 있어도 저장된 check 이력을 기준으로 재처리할 수 있다.
- 운영자가 장애 판정 근거를 DB 이력으로 확인할 수 있다.
- payload 크기와 producer / consumer 간 결합도를 줄일 수 있다.

## 6. Monitor Count 갱신 책임

Incident Detector 분리 후 monitor의 `failureCount`, `recoveryCount` 갱신 책임은 Incident Detector에 둔다.

성공 결과 처리:

- `CheckResult.isSuccess() == true`이면 `monitor.recordSuccess()`를 호출한다.
- `recoveryCount`는 1 증가한다.
- `failureCount`는 0으로 초기화된다.

실패 결과 처리:

- `CheckResult.isSuccess() == false`이면 `monitor.recordFailure()`를 호출한다.
- `failureCount`는 1 증가한다.
- `recoveryCount`는 0으로 초기화된다.

이 책임을 Check Worker에 두지 않는 이유:

- count 갱신은 incident open / resolve 판정과 한 처리 단위로 묶여야 한다.
- Check Worker는 URL 호출 결과 저장까지만 담당하는 편이 경계가 명확하다.
- 중복 `HealthCheckCompleted` 처리 시 count가 두 번 반영되지 않도록 Incident Detector가 idempotency를 관리해야 한다.

## 7. Incident Open 기준

Incident Detector는 다음 조건을 모두 만족할 때 incident를 open한다.

- `CheckResult`가 실패 상태다.
- monitor 상태가 `ACTIVE`다.
- `failureCount >= failureThreshold`다.
- 같은 monitor에 `OPEN` incident가 없다.

open 처리 순서:

```text
monitor.recordFailure()
-> monitor.canOpenIncident() 확인
-> IncidentRepository.existsByMonitorAndStatus(monitor, OPEN) 확인
-> Incident(status=OPEN, startedAt=checkedAt 또는 now, lastErrorMessage=실패 요약) 저장
-> monitor.markDown()
-> IncidentOpened 생성
```

`lastErrorMessage` 기준:

- `CheckResult.errorMessage`가 있으면 우선 사용한다.
- 없고 `httpStatus`가 있으면 `HTTP {status}` 형태로 사용한다.
- 둘 다 없으면 `CheckStatus.name()`을 사용한다.

## 8. 중복 Open 방지 기준

같은 monitor에 open incident는 하나만 존재해야 한다.

현재 코드 기준:

- `IncidentRepository.existsByMonitorAndStatus(monitor, IncidentStatus.OPEN)`으로 중복 생성을 막는다.

미래 분리 시 권장 기준:

- incident open 전에 monitor별 open incident 존재 여부를 반드시 조회한다.
- 가능하면 DB level unique constraint를 검토한다.
- 후보 constraint: `(monitor_id, status)` 중 `status = OPEN`에 대한 partial unique index
- DB가 partial unique index를 지원하지 않으면 별도 `open_incident` 제약이나 애플리케이션 lock을 검토한다.

이번 문서에서는 DB schema를 변경하지 않는다.

중복 이벤트 처리 기준:

- 같은 `checkResultId`로 이미 open 판정이 반영되었다면 다시 count를 올리거나 incident를 만들지 않는다.
- 이미 monitor가 `DOWN`이고 open incident가 있으면 추가 실패는 새 incident를 만들지 않는다.

## 9. Incident Resolve 기준

Incident Detector는 다음 조건을 모두 만족할 때 incident를 resolve한다.

- `CheckResult`가 성공 상태다.
- monitor 상태가 `DOWN`이다.
- `recoveryCount >= recoveryThreshold`다.
- 같은 monitor에 `OPEN` incident가 존재한다.

resolve 처리 순서:

```text
monitor.recordSuccess()
-> monitor.canRecover() 확인
-> IncidentRepository.findByMonitorAndStatus(monitor, OPEN) 조회
-> incident.resolve(checkedAt 또는 now)
-> monitor.recover()
-> IncidentResolved 생성
```

open incident가 없는 경우:

- 현재 코드에서는 예외가 발생한다.
- 미래 Incident Detector에서는 idempotent 처리를 우선한다.
- monitor가 `DOWN`인데 open incident가 없으면 운영상 불일치로 기록하고, 이벤트는 실패 재처리보다 별도 보정 대상으로 분리하는 것이 안전하다.

## 10. Resolve Idempotency 기준

복구 이벤트는 같은 incident에 대해 한 번만 생성되어야 한다.

기준:

- 이미 `RESOLVED` 상태인 incident는 다시 resolve하지 않는다.
- `resolvedAt`이 이미 있으면 `IncidentResolved`를 다시 만들지 않는다.
- 같은 `checkResultId`가 이미 반영되었다면 `recoveryCount`를 다시 증가시키지 않는다.
- `IncidentResolved` 중복 전달은 Notification Worker에서 incident, channel, notification type 기준으로 중복 발송을 막는다.

이번 문서에서는 처리 완료 기록용 테이블이나 schema 변경을 추가하지 않는다. 미래에는 `processed_event` 테이블 또는 `checkResultId` 기반 처리 이력을 검토한다.

## 11. Monitor 상태 전이 책임

Incident Detector 분리 후 monitor의 `ACTIVE` / `DOWN` 상태 전이 책임은 Incident Detector에 둔다.

상태 전이:

| 현재 상태 | 조건 | 다음 상태 | 처리 |
| --- | --- | --- | --- |
| `ACTIVE` | 실패 count가 threshold 미만 | `ACTIVE` | count만 갱신 |
| `ACTIVE` | 실패 count가 threshold 이상 | `DOWN` | incident open |
| `DOWN` | 성공 count가 threshold 미만 | `DOWN` | count만 갱신 |
| `DOWN` | 성공 count가 threshold 이상 | `ACTIVE` | incident resolve |
| `PAUSED` | 어떤 check 결과든 | `PAUSED` | 처리하지 않음 |

`PAUSED`나 soft deleted monitor는 Incident Detector가 처리하지 않는다. Check Worker가 이미 check를 수행했더라도, 처리 시점의 최신 monitor 상태를 기준으로 incident 판정을 건너뛴다.

## 12. IncidentOpened

`IncidentOpened`는 새 open incident가 DB에 저장되고 monitor가 `DOWN`으로 전이된 뒤 생성하는 논리 이벤트다.

Producer / consumer 후보:

| 구분 | 후보 |
| --- | --- |
| Producer | Incident Detector |
| Consumer | Notification Worker |

Payload 후보:

```text
eventId
occurredAt
incidentId
monitorId
memberId
startedAt
lastErrorMessage
sourceCheckResultId
```

생성 기준:

- 새 incident가 생성된 경우에만 생성한다.
- 이미 open incident가 있어 새 incident를 만들지 않았다면 생성하지 않는다.
- monitor 상태가 `DOWN`으로 전이된 뒤 생성한다.

## 13. IncidentResolved

`IncidentResolved`는 open incident가 resolved로 변경되고 monitor가 `ACTIVE`로 복구된 뒤 생성하는 논리 이벤트다.

Producer / consumer 후보:

| 구분 | 후보 |
| --- | --- |
| Producer | Incident Detector |
| Consumer | Notification Worker |

Payload 후보:

```text
eventId
occurredAt
incidentId
monitorId
memberId
startedAt
resolvedAt
sourceCheckResultId
```

생성 기준:

- open incident가 실제로 resolve된 경우에만 생성한다.
- 이미 resolved인 incident에 대해서는 생성하지 않는다.
- monitor 상태가 `ACTIVE`로 전이된 뒤 생성한다.

## 14. Transaction 경계

현재 구조에서는 `CheckResult` 저장, monitor count 갱신, incident 판정, monitor 상태 변경이 하나의 `CheckService.healthCheck` 트랜잭션에 묶여 있다.

미래 분리 시 권장 경계:

```text
Incident Detector transaction
-> CheckResult 조회
-> Monitor 조회 및 count 갱신
-> Incident open/resolve
-> Monitor 상태 전이
-> 처리 완료 기록 또는 outbox 기록
commit
-> IncidentOpened / IncidentResolved publish
```

권장 방향:

- incident DB 변경과 이벤트 발행 신뢰성을 맞추기 위해 outbox 패턴을 검토한다.
- 이번 단계에서는 outbox, Kafka, producer 구현을 하지 않는다.
- 현재 단일 앱에서는 알림 실패가 incident 판정을 롤백하지 않는 기존 원칙을 유지한다.

## 15. Idempotency 기준

`HealthCheckCompleted`는 중복 전달될 수 있다는 전제로 설계한다.

기본 기준:

- `checkResultId`는 Incident Detector 처리의 논리적 idempotency key다.
- 같은 `checkResultId`가 두 번 처리되어도 monitor count가 두 번 증가하면 안 된다.
- 같은 `checkResultId`로 incident open / resolve 이벤트가 두 번 생성되면 안 된다.

현재 schema에서의 한계:

- `CheckResult`에 incident 판정 처리 여부를 기록하는 필드가 없다.
- 별도 processed event 테이블이 없다.
- 따라서 이번 문서에서는 기준만 정리하고 DB 변경은 다음 구현 이슈로 분리한다.

미래 구현 후보:

- `incident_detector_processed_events(event_id, check_result_id, processed_at)` 테이블
- `CheckResult`에 `incidentProcessedAt` 필드 추가
- `(monitorId, scheduledAt)` 또는 `requestEventId` 기반 중복 방지 키

## 16. 현재 구조를 유지하는 이유

지금은 Kafka 없이 현재 동기 구조를 유지한다.

이유:

- MVP 기능은 단일 Spring Boot 앱에서 이미 동작한다.
- Incident Detector를 먼저 분리하면 장애 판정 정확도보다 운영 복잡도가 커진다.
- 현재는 open incident 중복 방지와 알림 실패 격리가 코드 안에서 단순하게 유지된다.
- Worker 분리는 책임 경계를 문서로 고정한 뒤 실제 부하나 운영 필요가 생겼을 때 진행하는 편이 안전하다.

## 17. 이번 문서에서 하지 않은 일

- Kafka 의존성을 추가하지 않았다.
- producer / consumer 코드를 만들지 않았다.
- 별도 Incident Detector 애플리케이션을 만들지 않았다.
- DB schema를 변경하지 않았다.
- outbox 또는 processed event 테이블을 만들지 않았다.
- Notification Worker를 구현하지 않았다.

## 18. 다음 작업

다음 이슈는 `Notification Worker 분리 설계`다.

다음 문서에서 다룰 내용:

- `IncidentOpened`, `IncidentResolved` 처리 기준
- 채널별 `NotificationRequested` 생성 기준
- `NotificationHistory` 생성 책임
- 같은 incident, channel, notification type 중복 발송 방지 기준
- webhook target 원문을 이벤트 payload에 넣지 않는 보안 기준
