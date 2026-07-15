# Pingbell Check Worker Design

작성일: 2026-06-29

## 1. 목적

이 문서는 `docs/event-boundary.md`에서 정의한 이벤트 경계를 바탕으로 현재 `CheckScheduler`와 `CheckService.healthCheck`의 책임을 Check Worker 분리 관점에서 정리한다.

이번 작업은 설계 문서 작업이다. Kafka, message producer, message consumer, 별도 Worker 애플리케이션은 구현하지 않는다.

## 2. 현재 동기 구조

현재 health check는 단일 Spring Boot 애플리케이션 안에서 다음 순서로 동작한다.

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

현재 `CheckScheduler`의 책임:

- 5초마다 scheduler trigger를 실행한다.
- `Clock` 기준의 현재 시각을 만든다.
- `CheckService.healthCheck(now)`를 호출한다.

현재 `CheckService.healthCheck`의 책임:

- `ACTIVE`, `DOWN` 상태이고 `nextCheckAt <= now`인 monitor를 조회한다.
- 각 monitor의 URL을 timeout 설정에 맞춰 호출한다.
- HTTP status, timeout, 예외, 응답 시간을 기준으로 check status를 분류한다.
- `CheckResult`를 저장한다.
- 성공/실패 결과에 따라 monitor의 연속 성공/실패 count를 갱신한다.
- threshold 기준에 따라 incident를 open 또는 resolve한다.
- incident open/resolve 시 알림 서비스를 호출한다.
- 처리 후 monitor의 `nextCheckAt`을 다음 실행 시각으로 갱신한다.

## 3. 미래 분리 목표

Check Worker 분리 후에도 현재 사용자가 보는 기능은 유지되어야 한다.

분리 목표는 다음과 같다.

- Scheduler는 due monitor를 찾아 check 요청을 만드는 책임만 가진다.
- Check Worker는 실제 URL 호출과 `CheckResult` 저장을 맡는다.
- Incident Detector는 `CheckResult` 이후의 monitor count 갱신, incident open/resolve 판정을 맡는다.
- Notification Worker는 incident 이후의 알림 생성과 발송을 맡는다.

이번 문서에서는 Check Worker 경계까지만 다룬다. Incident Detector와 Notification Worker의 상세 설계는 다음 이슈에서 다룬다.

## 4. HealthCheckRequested

### 4.1 목적

`HealthCheckRequested`는 due monitor에 대해 URL 호출을 수행하라는 요청이다.

미래 구조에서 producer와 consumer 후보:

| 구분 | 후보 |
| --- | --- |
| Producer | Check Scheduler |
| Consumer | Check Worker |

### 4.2 생성 기준

`HealthCheckRequested`는 다음 조건을 만족하는 monitor마다 생성한다.

- `deletedAt IS NULL`
- `status IN (ACTIVE, DOWN)`
- `nextCheckAt <= now`

현재 코드 기준으로는 `MonitorRepository.findAllByStatusInAndDeletedAtIsNullAndNextCheckAtLessThanEqual(...)` 조회 결과가 이벤트 생성 대상이다.

### 4.3 Payload 후보

```text
eventId
occurredAt
monitorId
memberId
url
timeoutMillis
intervalSeconds
scheduledAt
requestedBy
```

필드 기준:

- `eventId`: 이벤트 중복 처리와 추적을 위한 고유 id
- `occurredAt`: 이벤트 생성 시각
- `monitorId`: check 대상 monitor id
- `memberId`: 소유자 식별자
- `url`: 호출 대상 URL
- `timeoutMillis`: URL 호출 timeout
- `intervalSeconds`: 다음 check 예약 계산에 필요한 주기
- `scheduledAt`: 이 check가 예정되었던 시각, 보통 기존 `nextCheckAt`
- `requestedBy`: `SCHEDULER`, `MANUAL` 같은 요청 출처 확장용 값

### 4.4 Payload 보안 기준

- `url`은 check 실행에 필요하지만 외부 공개 로그나 알림 payload에 남기지 않는다.
- 인증 토큰, webhook target, secret 값은 check 요청 이벤트에 넣지 않는다.
- Worker가 처리 시점에 DB에서 monitor를 다시 조회하면 URL 변경, pause, delete 같은 최신 상태를 확인할 수 있다.

## 5. HealthCheckCompleted

### 5.1 목적

`HealthCheckCompleted`는 Check Worker가 URL 호출을 마치고 `CheckResult` 저장까지 완료했음을 알리는 이벤트다.

미래 구조에서 producer와 consumer 후보:

| 구분 | 후보 |
| --- | --- |
| Producer | Check Worker |
| Consumer | Incident Detector |

### 5.2 생성 기준

Check Worker가 다음 작업을 완료한 뒤 생성한다.

1. monitor 상태와 설정을 확인한다.
2. URL을 호출한다.
3. 결과를 `SUCCESS`, `FAILURE`, `TIMEOUT`, `HTTP_ERROR`, `SLOW_RESPONSE` 중 하나로 분류한다.
4. `CheckResult`를 저장한다.

### 5.3 Payload 후보

```text
eventId
occurredAt
requestEventId
monitorId
checkResultId
status
httpStatus
responseTimeMs
errorMessage
checkedAt
```

필드 기준:

- `requestEventId`: 어떤 `HealthCheckRequested`를 처리한 결과인지 연결한다.
- `checkResultId`: 저장된 `CheckResult` id
- `status`: `CheckStatus` 값
- `httpStatus`: HTTP 응답 코드, 호출 실패나 timeout이면 null 가능
- `responseTimeMs`: 측정된 응답 시간
- `errorMessage`: 예외 class name 또는 실패 요약
- `checkedAt`: 실제 check 수행 시각

### 5.4 Source of Truth

`HealthCheckCompleted` payload에 결과 값을 포함하더라도 source of truth는 DB의 `CheckResult`로 둔다.

이유:

- 이벤트 payload가 유실되거나 중복 처리되어도 저장된 check 이력을 기준으로 재처리할 수 있다.
- Incident Detector가 상세 판단에 필요한 값을 DB에서 다시 조회할 수 있다.
- 이벤트 크기를 필요 이상으로 키우지 않을 수 있다.

## 6. CheckResult 저장 책임

Check Worker 분리 후 `CheckResult` 저장 책임은 Check Worker에 둔다.

근거:

- `CheckResult`는 URL 호출의 직접 결과다.
- URL 호출 성공/실패, timeout, 응답 시간 측정은 Check Worker가 가장 정확히 알고 있다.
- Incident Detector가 저장을 맡으면 URL 호출 결과 이벤트와 DB 이력 저장 사이에 중복/누락 처리가 복잡해진다.

권장 순서:

```text
HealthCheckRequested consume
-> monitor 최신 상태 조회
-> URL 호출
-> CheckResult 저장
-> HealthCheckCompleted produce
```

## 7. nextCheckAt 갱신 책임

Check Worker 분리 이후에도 `nextCheckAt` 갱신은 check 요청을 확정하거나 check 결과를 저장하는 단계에서 한 번만 수행해야 한다.

권장안은 Check Worker가 `CheckResult` 저장과 함께 `nextCheckAt`을 갱신하는 것이다.

이유:

- 현재 구조에서는 check 1회 처리 후 `monitor.updateNextCheckedAt(now.plusSeconds(intervalSeconds))`가 실행된다.
- Scheduler가 이벤트 발행 시점에 먼저 `nextCheckAt`을 갱신하면 Worker 실패 시 실제 check가 수행되지 않았는데 다음 시각으로 밀릴 수 있다.
- Check Worker가 저장과 갱신을 같은 처리 단위로 묶으면 "결과가 저장된 check만 다음 예약으로 이동한다"는 기준을 유지할 수 있다.

주의할 점:

- Worker가 실패하면 같은 due monitor가 다시 요청될 수 있다.
- 중복 요청을 줄이려면 나중에 `inProgress`, lease, claim token 같은 별도 상태가 필요할 수 있다.
- 이번 설계에서는 추가 상태 컬럼을 만들지 않는다.

## 8. Pingbell 서버 실패와 URL 호출 실패 구분

외부 URL 호출 실패는 Pingbell 서버 장애가 아니라 개별 monitor의 check 실패로 기록한다.

CheckResult 분류 기준:

| 상황 | CheckStatus | 처리 기준 |
| --- | --- | --- |
| 2xx/3xx 응답이고 timeout 이하 | `SUCCESS` | 성공 count 증가 대상 |
| 4xx/5xx 응답 | `HTTP_ERROR` | 실패 count 증가 대상 |
| timeout 예외 | `TIMEOUT` | 실패 count 증가 대상 |
| 응답 시간이 `timeoutMillis` 초과 | `SLOW_RESPONSE` | 실패 count 증가 대상 |
| 그 외 호출 실패 | `FAILURE` | 실패 count 증가 대상 |

Pingbell 서버 실패로 봐야 하는 경우:

- Worker 프로세스가 이벤트를 처리하지 못한다.
- DB 저장이 실패한다.
- 이벤트 발행 또는 commit이 실패한다.
- application 설정 오류로 모든 check가 실행되지 않는다.

이 구분은 운영 지표와 알림에서도 유지한다. 사용자 monitor 장애 알림은 `CheckResult`와 `Incident` 기준으로 보내고, Pingbell 내부 장애는 별도 운영 알림 대상으로 다룬다.

## 9. Idempotency 기준

Check Worker 분리 후에도 다음 기준은 유지되어야 한다.

### 9.1 HealthCheckRequested 중복

같은 monitor에 대한 요청이 중복으로 들어올 수 있다.

기본 처리 기준:

- Worker는 처리 시작 시 monitor를 DB에서 다시 조회한다.
- monitor가 삭제되었거나 `PAUSED`면 처리하지 않는다.
- monitor의 현재 `nextCheckAt`이 요청의 `scheduledAt`보다 미래라면 이미 처리된 요청으로 보고 skip할 수 있다.

### 9.2 CheckResult 중복 저장

미래에는 `requestEventId` 또는 `(monitorId, scheduledAt)` 기준의 중복 방지 키를 고려한다.

이번 단계에서는 DB schema를 변경하지 않으므로 문서 기준만 둔다.

권장 기준:

- 같은 `requestEventId`는 하나의 `CheckResult`만 만들 수 있어야 한다.
- `requestEventId`가 없다면 `(monitorId, scheduledAt)`을 논리적 idempotency key로 본다.

### 9.3 HealthCheckCompleted 중복

`HealthCheckCompleted`가 중복 전달되어도 Incident Detector는 같은 `checkResultId`를 두 번 반영하지 않아야 한다.

이 기준은 다음 `Incident Detector 분리 설계`에서 더 구체화한다.

## 10. 현재 구조를 유지하는 이유

지금은 Kafka 없이 현재 동기 구조를 유지한다.

이유:

- MVP 1차 기능은 단일 Spring Boot 앱과 Scheduler만으로 동작한다.
- Kafka를 먼저 도입하면 장애 판정 정확도보다 운영 복잡도가 먼저 커진다.
- 현재 코드도 알림 실패가 check 저장과 incident 판정을 막지 않도록 예외를 격리하고 있다.
- Check Worker 분리는 설계 경계를 먼저 정리한 뒤, 실제 부하나 운영 필요가 생겼을 때 구현하는 편이 안전하다.

## 11. 이번 문서에서 하지 않은 일

- Kafka 의존성을 추가하지 않았다.
- producer / consumer 코드를 만들지 않았다.
- 별도 Check Worker 애플리케이션을 만들지 않았다.
- DB schema를 변경하지 않았다.
- Incident Detector와 Notification Worker를 구현하지 않았다.

## 12. 다음 작업

다음 이슈는 `Incident Detector 분리 설계`다.

다음 문서에서 다룰 내용:

- `HealthCheckCompleted` 처리 기준
- monitor failure/recovery count 갱신 책임
- incident open 중복 방지 기준
- incident resolve idempotency 기준
- monitor `ACTIVE` / `DOWN` 상태 전이 책임
