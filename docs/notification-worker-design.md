# Pingbell Notification Worker Design

작성일: 2026-06-29

## 1. 목적

이 문서는 `IncidentOpened`, `IncidentResolved` 이후 채널별 알림 생성과 발송 책임을 미래 `Notification Worker` 분리 관점에서 정리한다.

이번 작업은 설계 문서 작업이다. Kafka, message producer, message consumer, 별도 Notification Worker 애플리케이션은 구현하지 않는다.

## 2. 현재 동기 구조

현재 incident 알림은 단일 Spring Boot 애플리케이션 안에서 `CheckService.healthCheck(now)`가 incident open / resolve를 처리한 뒤 `NotificationService`를 호출하는 구조다.

```text
CheckService
-> NotificationService.notifyIncidentOpened(...) 또는 notifyIncidentResolved(...)
-> NotificationChannelRepository.findAllByMemberAndEnabledTrue(...)
-> 필요 시 기본 EMAIL NotificationChannel 생성
-> NotificationHistoryRepository.existsByIncidentAndChannelAndNotificationType(...)
-> NotificationHistory 저장
-> NotificationSender.send(...)
-> NotificationHistory.markSent(...) 또는 markRetryPending(...) 또는 markFailed(...)
```

현재 `NotificationService`의 책임:

- incident open / resolve에 맞는 `NotificationType`을 선택한다.
- incident monitor의 member 기준으로 enabled channel을 조회한다.
- enabled channel이 없고 기존 채널도 없으면 기본 EMAIL channel을 생성한다.
- 같은 incident, channel, notification type에 대한 중복 이력이 있으면 발송하지 않는다.
- 채널별 `NotificationHistory`를 `PENDING` 상태로 생성한다.
- channel type에 맞는 sender를 찾는다.
- 알림 메시지를 만든다.
- sender로 EMAIL / SLACK / DISCORD 알림을 발송한다.
- 발송 성공 시 `SENT`로 기록한다.
- retryable 실패 시 `RETRY_PENDING`과 `nextRetryAt`을 기록한다.
- non-retryable 실패 시 `FAILED`로 기록한다.

현재 트랜잭션 특징:

- `NotificationService.notifyIncidentOpened/Resolved`는 `REQUIRES_NEW`로 실행된다.
- `CheckService`는 알림 예외를 catch 해서 check 결과 저장과 incident 판정이 알림 실패로 롤백되지 않게 한다.

## 3. 미래 분리 목표

Notification Worker 분리 후 책임 경계는 다음과 같다.

| 구성 | 책임 |
| --- | --- |
| Incident Detector | incident open / resolve 처리 후 `IncidentOpened`, `IncidentResolved` 생성 |
| Notification Worker | incident 이벤트를 받아 채널별 `NotificationHistory`와 발송 요청 생성 |
| Notification Sender | channel type별 실제 EMAIL / SLACK / DISCORD 발송 |
| Retry / DLQ Handler | retry exhausted 또는 최종 실패 처리 |

이번 문서에서는 Notification Worker 경계까지만 다룬다. DLQ / 재처리 정책은 다음 이슈에서 다룬다.

## 4. IncidentOpened 처리 기준

`IncidentOpened`는 새 open incident가 생성되고 monitor가 `DOWN`으로 전이된 뒤 만들어지는 논리 이벤트다.

Notification Worker의 기본 처리 순서:

```text
IncidentOpened consume
-> incidentId 기준으로 Incident 조회
-> monitor, member 조회
-> enabled channel 조회
-> 필요 시 기본 EMAIL channel 생성
-> channel별 중복 NotificationHistory 존재 여부 확인
-> NotificationHistory 생성
-> NotificationRequested 생성 또는 현재 구조에서는 즉시 sender 호출
```

처리 가능 조건:

- incident가 존재해야 한다.
- incident 상태가 `OPEN`이어야 한다.
- incident monitor가 존재해야 한다.
- monitor member가 존재해야 한다.

처리하지 않는 조건:

- incident가 존재하지 않는다.
- incident가 이미 `RESOLVED`인데 `IncidentOpened`가 늦게 도착했다.
- 같은 incident, channel, `INCIDENT_OPEN` 이력이 이미 존재한다.

## 5. IncidentResolved 처리 기준

`IncidentResolved`는 open incident가 resolved로 변경되고 monitor가 `ACTIVE`로 복구된 뒤 만들어지는 논리 이벤트다.

Notification Worker의 기본 처리 순서:

```text
IncidentResolved consume
-> incidentId 기준으로 Incident 조회
-> incident resolved 상태 확인
-> monitor, member 조회
-> enabled channel 조회
-> channel별 중복 NotificationHistory 존재 여부 확인
-> NotificationHistory 생성
-> NotificationRequested 생성 또는 현재 구조에서는 즉시 sender 호출
```

처리 가능 조건:

- incident가 존재해야 한다.
- incident 상태가 `RESOLVED`여야 한다.
- `resolvedAt`이 존재해야 한다.
- incident monitor와 member가 존재해야 한다.

처리하지 않는 조건:

- incident가 아직 `OPEN` 상태다.
- `resolvedAt`이 없다.
- 같은 incident, channel, `INCIDENT_RESOLVED` 이력이 이미 존재한다.

## 6. Channel 조회와 기본 EMAIL 기준

Notification Worker는 incident monitor의 member 기준으로 알림 채널을 조회한다.

현재 코드 기준:

- `NotificationChannelRepository.findAllByMemberAndEnabledTrue(member)`로 enabled channel을 조회한다.
- enabled channel이 하나 이상 있으면 해당 채널들로만 발송한다.
- enabled channel이 없고 기존 channel도 없으면 member email로 기본 EMAIL channel을 생성한다.
- 기존 channel이 하나라도 있으면 기본 EMAIL channel을 새로 만들지 않는다.

미래 분리 후에도 이 기준을 유지한다.

주의할 점:

- disabled channel에는 알림 요청을 만들지 않는다.
- 기본 EMAIL 자동 생성은 "사용자가 알림 채널을 한 번도 만들지 않은 경우"로 제한한다.
- 사용자가 모든 채널을 disabled 처리한 경우에는 의도적으로 알림을 받지 않겠다는 상태로 본다.

## 7. NotificationRequested

`NotificationRequested`는 특정 incident 알림을 특정 channel로 발송하라는 요청이다.

Producer / consumer 후보:

| 구분 | 후보 |
| --- | --- |
| Producer | Notification Worker |
| Consumer | Notification Sender Worker |

Payload 후보:

```text
eventId
occurredAt
notificationHistoryId
incidentId
memberId
channelId
channelType
notificationType
requestedBy
```

필드 기준:

- `notificationHistoryId`: 발송 상태 변경의 기준이 되는 이력 id
- `incidentId`: 알림 대상 incident
- `memberId`: 소유자와 라우팅 확인용
- `channelId`: sender가 target을 DB에서 조회하기 위한 id
- `channelType`: sender 선택을 위한 값
- `notificationType`: `INCIDENT_OPEN` 또는 `INCIDENT_RESOLVED`
- `requestedBy`: `INCIDENT_EVENT`, `MANUAL_RESEND`, `RETRY` 같은 출처 확장용 값

## 8. NotificationHistory 생성 책임

Notification Worker 분리 후 `NotificationHistory` 생성 책임은 Notification Worker에 둔다.

기준:

- channel별 발송 요청을 만들기 전에 `NotificationHistory`를 먼저 생성한다.
- 생성 시 초기 상태는 `PENDING`이다.
- `NotificationRequested`에는 생성된 `notificationHistoryId`를 포함한다.
- Sender는 `notificationHistoryId` 기준으로 이력을 조회하고 발송 결과를 반영한다.

이유:

- 알림 요청이 만들어진 사실을 DB에 먼저 남길 수 있다.
- sender 실패나 이벤트 유실이 있어도 `PENDING` 또는 `RETRY_PENDING` 이력을 기준으로 재처리할 수 있다.
- 중복 발송 방지 기준을 `NotificationHistory`에 둘 수 있다.

## 9. 중복 발송 방지 기준

같은 incident, channel, notification type에 대해서는 알림 이력이 하나만 생성되어야 한다.

현재 코드 기준:

- `NotificationHistoryRepository.existsByIncidentAndChannelAndNotificationType(incident, channel, type)`으로 중복 발송을 막는다.

미래 분리 시 권장 기준:

- `NotificationHistory` 생성 전에 같은 incident, channel, notification type 이력이 있는지 확인한다.
- 가능하면 DB unique constraint를 검토한다.
- 후보 unique key: `(incident_id, channel_id, notification_type, manual_resend=false)`
- 수동 재전송은 감사 이력을 위해 별도 row를 만들기 때문에 unique key 설계 시 예외가 필요하다.

이번 문서에서는 DB schema를 변경하지 않는다.

중복 이벤트 처리 기준:

- 같은 `IncidentOpened`가 중복 전달되어도 channel별 open 알림 이력은 한 번만 만든다.
- 같은 `IncidentResolved`가 중복 전달되어도 channel별 resolved 알림 이력은 한 번만 만든다.
- `NotificationRequested`가 중복 전달되면 Sender는 같은 `notificationHistoryId`가 이미 `SENT`인지 확인하고 재발송하지 않아야 한다.

## 10. Sender 책임

현재 구조에서는 `NotificationService`가 sender를 직접 호출한다.

미래 분리 후 권장 책임:

| 구성 | 책임 |
| --- | --- |
| Notification Worker | channel 조회, 중복 이력 확인, `NotificationHistory` 생성, `NotificationRequested` 생성 |
| Notification Sender Worker | `notificationHistoryId` 조회, message 생성, sender 호출, 발송 결과 반영 |

Sender 처리 순서:

```text
NotificationRequested consume
-> NotificationHistory 조회
-> history 상태 확인
-> channel enabled 확인
-> message 생성
-> channel type에 맞는 sender 호출
-> 성공 시 markSent
-> retryable 실패 시 markRetryPending
-> non-retryable 실패 시 markFailed
```

이번 단계에서는 별도 Sender Worker를 구현하지 않는다.

## 11. Retryable / Non-retryable 실패 연결

현재 실패 분류 기준은 `NotificationFailureClassifier`가 맡는다.

retryable 후보:

- webhook timeout 또는 네트워크 접근 실패
- Slack / Discord webhook 5xx
- Slack / Discord webhook 429
- SMTP 계열 일시 실패

non-retryable 후보:

- webhook 400 / 401 / 403 / 404
- email target 형식 오류
- 인증 실패
- target 복호화 실패
- 지원하지 않는 channel type
- payload 생성 실패

미래 분리 후에도 발송 실패 분류는 Sender 쪽에서 수행한다.

기준:

- retryable 실패는 `NotificationHistory.markRetryPending(...)`으로 남긴다.
- non-retryable 실패는 `NotificationHistory.markFailed(...)`로 남긴다.
- retry scheduling은 현재 `NotificationRetryScheduler` 흐름을 유지하되, 미래에는 Retry / DLQ Handler로 분리할 수 있다.

## 12. Manual Resend 연결

현재 수동 재전송은 실패한 `NotificationHistory`를 기준으로 새 이력을 만든다.

미래 구조에서 수동 재전송은 `NotificationRequested`의 `requestedBy=MANUAL_RESEND` 흐름으로 볼 수 있다.

기준:

- 원본 이력은 덮어쓰지 않는다.
- 새 이력에는 `manualResend=true`, `resendOfHistoryId=원본 id`를 기록한다.
- 발송 target은 원본 target이 아니라 현재 enabled channel의 최신 target을 사용한다.
- 수동 재전송 이력도 retryable 실패면 자동 재시도 흐름에 연결될 수 있다.

## 13. 보안 기준

이벤트 payload에는 알림 target 원문을 넣지 않는다.

금지 payload:

- email address 원문
- Slack webhook URL
- Discord webhook URL
- 복호화된 target
- 인증 토큰 또는 secret

허용 payload:

- `channelId`
- `channelType`
- `notificationHistoryId`
- `incidentId`
- `memberId`
- `notificationType`

이유:

- webhook URL은 외부에서 메시지를 보낼 수 있는 secret이다.
- 이벤트 로그, broker, DLQ, 운영 대시보드에 payload가 남을 수 있다.
- sender가 처리 시점에 DB에서 channel target을 조회하면 최신 target과 enabled 상태를 확인할 수 있다.

## 14. Transaction 경계

현재 구조:

- `NotificationService`가 `REQUIRES_NEW` 트랜잭션으로 실행된다.
- 알림 실패는 incident 판정 트랜잭션을 롤백하지 않는다.

미래 분리 시 권장 경계:

```text
Notification Worker transaction
-> Incident 조회
-> channel 조회 또는 기본 EMAIL channel 생성
-> 중복 NotificationHistory 확인
-> NotificationHistory 생성
-> NotificationRequested outbox 기록
commit
-> NotificationRequested publish
```

```text
Notification Sender transaction
-> NotificationHistory 조회
-> sender 호출
-> markSent / markRetryPending / markFailed
commit
```

이번 단계에서는 outbox, Kafka, producer 구현을 하지 않는다.

## 15. Idempotency 기준

Notification Worker 분리 후에도 다음 기준은 유지되어야 한다.

Incident 이벤트 중복:

- 같은 incident, channel, notification type 이력이 이미 있으면 새 이력을 만들지 않는다.
- 이미 이력이 있으면 `NotificationRequested`도 새로 만들지 않는다.

NotificationRequested 중복:

- 같은 `notificationHistoryId`의 요청이 중복 전달될 수 있다.
- history가 이미 `SENT`이면 재발송하지 않는다.
- history가 `FAILED`이고 retryable이 false이면 자동 재발송하지 않는다.
- history가 `RETRY_PENDING`이면 retry scheduler 또는 retry handler의 기준을 따른다.

Manual resend:

- 수동 재전송은 사용자의 명시적 요청이므로 새 이력을 만든다.
- 자동 중복 방지 기준과 수동 재전송 이력 생성 기준을 구분한다.

## 16. 현재 구조를 유지하는 이유

지금은 Kafka 없이 현재 동기 구조를 유지한다.

이유:

- MVP 기능은 단일 Spring Boot 앱에서 동작한다.
- 현재 `REQUIRES_NEW`와 try-catch 격리만으로도 알림 실패가 incident 판정을 롤백하지 않는다.
- Kafka와 별도 Worker를 먼저 도입하면 알림 신뢰성보다 운영 복잡도가 먼저 커진다.
- 현재 자동 재시도와 수동 재전송 정책이 이미 단일 앱 구조에서 검증 가능하다.
- Worker 분리는 책임 경계를 문서로 고정한 뒤 실제 부하나 운영 필요가 생겼을 때 진행하는 편이 안전하다.

## 17. 이번 문서에서 하지 않은 일

- Kafka 의존성을 추가하지 않았다.
- producer / consumer 코드를 만들지 않았다.
- 별도 Notification Worker 애플리케이션을 만들지 않았다.
- 별도 Notification Sender Worker를 만들지 않았다.
- DB schema를 변경하지 않았다.
- outbox 또는 DLQ를 만들지 않았다.

## 18. 다음 작업

다음 이슈는 `DLQ / 재처리 정책 설계`다.

다음 문서에서 다룰 내용:

- check, incident, notification 단계별 retryable / non-retryable 실패 기준
- retry exhausted 이후 최종 실패 처리 기준
- DLQ 후보 이벤트와 저장 대상
- 자동 재처리와 수동 재처리 구분
- 운영자가 확인해야 할 재처리 로그와 상태 기준
