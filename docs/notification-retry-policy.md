# Pingbell 알림 재시도 정책

작성일: 2026-06-22

## 1. 현재 판단

Pingbell은 현재 알림 발송 실패를 재시도 가능 여부와 알림 중요도에 따라 다르게 기록한다. 새 자동 재시도 예약은 `FAILED + retryable=true + nextRetryAt` 조합으로 저장하며, 기존 `RETRY_PENDING` 상태는 호환성을 위해 유지한다.

MVP 2 이후 다음 단계에서는 Kafka나 별도 notification worker를 바로 도입하지 않고, 단일 Spring Boot 애플리케이션 안에서 제한적인 재시도 정책을 먼저 구현한다. 목적은 일시적인 네트워크 오류나 외부 알림 서비스의 짧은 장애를 흡수하되, 잘못된 target이나 권한 문제처럼 재시도해도 성공 가능성이 낮은 실패를 반복 호출하지 않는 것이다.

## 2. 작업 범위

이번 정책의 범위는 다음과 같다.

- 알림 발송 실패 시 재시도 대상과 제외 대상 정의
- 자동 재시도 횟수와 backoff 간격 정의
- 최종 실패 처리 기준 정의
- 수동 재전송 기능 포함 여부 판단
- `NotificationHistory` 상태 전이와 필요한 필드 정리
- Backend 구현 이슈 단위 분해

## 3. 제외 범위

다음은 이번 단계에서 제외한다.

- Kafka / Worker 분리
- DLQ 구현
- Slack OAuth / Discord OAuth
- 사용자별 재시도 정책 커스터마이징
- 알림 발송 우선순위 큐
- 대량 발송 처리
- 프론트 수동 재전송 화면 구현

## 4. 재시도 대상

자동 재시도 대상은 일시적 실패 가능성이 있는 케이스로 제한한다.

- EMAIL SMTP 연결 실패
- EMAIL SMTP timeout
- Slack / Discord webhook 호출 timeout
- Slack / Discord webhook 5xx 응답
- Slack / Discord webhook 429 응답
- DNS, connect timeout, read timeout 같은 네트워크 예외
- 알림 sender 내부에서 일시적 외부 호출 실패로 분류한 예외

재시도 대상 여부는 sender가 던진 예외 또는 응답 정보를 기반으로 `retryable=true`로 기록한다.

## 5. 재시도 제외 대상

다음 실패는 자동 재시도하지 않는다.

- EMAIL target 형식 오류
- Slack / Discord webhook URL 형식 오류
- Slack / Discord webhook 400 응답
- Slack / Discord webhook 401 / 403 응답
- Slack / Discord webhook 404 응답
- 알림 채널이 disabled 상태인 경우
- target 복호화 실패
- 지원하지 않는 알림 채널 타입
- payload 생성 실패 같은 애플리케이션 내부 데이터 오류

이 케이스는 재시도해도 성공 가능성이 낮거나 운영자가 target을 수정해야 하는 문제이므로 즉시 최종 실패로 기록한다.

## 6. 자동 재시도 정책

기본 정책:

- 장애 발생 알림(`INCIDENT_OPEN`)
  - 최초 실패 직후 즉시 1회 재시도한다.
  - 예약 재시도 backoff 기본값은 `30초 -> 1분 -> 3분`이다.
  - 최대 재시도 횟수 기본값은 4회다.
- 장애 복구 알림(`INCIDENT_RESOLVED`)
  - 즉시 재시도 없이 예약 재시도만 수행한다.
  - 예약 재시도 backoff 기본값은 `1분 -> 5분`이다.
  - 최대 재시도 횟수 기본값은 2회다.
- 재시도 실행 방식: Spring Scheduler 기반 polling
- 재시도 대상 조회 조건:
  - `retryable = true`
  - `nextRetryAt <= now`
  - `retryCount < maxRetryCount`

초기 구현에서는 지수 backoff나 jitter를 넣지 않는다. 단일 앱 MVP 단계에서는 타입별 고정 간격이 더 단순하고 테스트하기 쉽다. Kafka worker 분리 이후에 메시지 재처리와 함께 backoff 전략을 고도화한다.

## 7. 최종 실패 처리

다음 조건 중 하나를 만족하면 최종 실패로 본다.

- 재시도 제외 대상 실패가 발생했다.
- 재시도 대상 실패지만 최대 재시도 횟수를 모두 사용했다.
- 재시도 중 같은 `NotificationHistory`의 채널이 disabled 상태가 됐다.
- 재시도 중 target 복호화에 실패했다.

최종 실패 시 `NotificationHistory.status`는 `FAILED`가 된다.

## 8. 수동 재전송 판단

수동 재전송 기능은 이번 자동 재시도 구현 이슈에는 포함하지 않는다.

이유:

- 현재 프론트에는 알림 이력 목록만 있고 상세 액션 정책이 없다.
- 잘못된 target을 수정한 뒤 기존 실패 이력을 재전송할지, 새 알림 이력으로 남길지 결정이 필요하다.
- 자동 재시도 상태 전이와 수동 재전송 상태 전이를 한 번에 넣으면 검증 범위가 커진다.

따라서 수동 재전송은 별도 PM / Backend / Frontend 이슈로 분리한다.

권장 정책:

- 수동 재전송은 기존 `NotificationHistory`를 덮어쓰지 않고 새 `NotificationHistory`를 만든다.
- 원본 이력과 재전송 이력은 `originalHistoryId` 같은 필드로 연결한다.
- 수동 재전송은 target이 수정된 최신 enabled channel 기준으로 발송한다.

## 9. NotificationHistory 상태 전이

현재 상태:

- `PENDING`
- `SENT`
- `FAILED`

추가 권장 상태:

- `RETRY_PENDING`

상태 전이:

```text
PENDING -> SENT
PENDING -> RETRY_PENDING
PENDING -> FAILED
RETRY_PENDING -> SENT
RETRY_PENDING -> RETRY_PENDING
RETRY_PENDING -> FAILED
```

의미:

- `PENDING`: 최초 발송 시도 전 또는 시도 중
- `SENT`: 발송 성공
- `RETRY_PENDING`: 과거 구현에서 재시도 대상 실패가 발생했고 다음 재시도를 기다리는 상태
- `FAILED`: 재시도 불가 또는 최대 재시도 초과로 최종 실패

현재 신규 자동 재시도 예약은 `RETRY_PENDING` 대신 `FAILED + retryable=true + nextRetryAt != null + retryCount < maxRetryCount`로 표현한다. 따라서 화면과 문서는 이 조합을 최종 실패가 아니라 "재시도 예정"으로 표시해야 한다.

## 10. 필요한 필드

`NotificationHistory`에 다음 필드 추가를 권장한다.

| 필드 | 타입 예시 | 설명 |
| --- | --- | --- |
| `retryCount` | integer | 현재까지 수행한 재시도 횟수 |
| `maxRetryCount` | integer | 이 이력에 적용된 최대 재시도 횟수 |
| `nextRetryAt` | timestamp nullable | 다음 재시도 예정 시각 |
| `lastAttemptedAt` | timestamp nullable | 마지막 발송 시도 시각 |
| `retryable` | boolean | 마지막 실패가 재시도 대상인지 여부 |

이미 존재하는 실패 사유 필드는 유지하되, 외부 URL이나 webhook URL은 현재 정책처럼 마스킹해서 저장 또는 응답해야 한다.

## 11. Backend 구현 이슈 분해

### Issue 1. NotificationHistory 재시도 상태와 필드 추가

담당 에이전트: Backend

작업 범위:

- `NotificationStatus.RETRY_PENDING` 추가
- `NotificationHistory`에 재시도 관련 필드 추가
- Flyway migration 작성
- 기존 데이터 기본값 정리

완료 조건:

- 기존 `PENDING / SENT / FAILED` 동작이 유지된다.
- `RETRY_PENDING` 상태를 저장할 수 있다.
- migration 적용 후 기존 테스트가 통과한다.

### Issue 2. 알림 실패 분류 모델 추가

담당 에이전트: Backend

작업 범위:

- sender 실패 결과를 retryable / non-retryable로 분류
- EMAIL / SLACK / DISCORD sender별 실패 분류 기준 적용
- 실패 사유는 webhook URL을 노출하지 않도록 유지

완료 조건:

- 5xx / timeout은 retryable 실패로 기록된다.
- 4xx 인증, 권한, not found 계열은 non-retryable 실패로 기록된다.
- target 복호화 실패는 non-retryable 실패로 기록된다.

### Issue 3. 자동 재시도 Scheduler 구현

담당 에이전트: Backend

작업 범위:

- `RETRY_PENDING` 이력 중 `nextRetryAt <= now` 대상 조회
- 최대 재시도 횟수 이내면 재발송
- 성공 시 `SENT` 처리
- 실패 시 다음 backoff 또는 최종 `FAILED` 처리
- 알림 재시도 실패가 health check나 incident 판정을 롤백하지 않도록 분리

완료 조건:

- 최초 실패 후 1분 뒤 1차 재시도 대상으로 잡힌다.
- 1차 재시도 실패 후 5분 뒤 2차 재시도 대상으로 잡힌다.
- 2차 재시도 실패 후 `FAILED`로 최종 처리된다.
- 재시도 성공 시 `SENT`로 변경된다.

### Issue 4. NotificationHistory 조회 응답 보완

담당 에이전트: Backend / Frontend

작업 범위:

- 조회 API에 `retryCount`, `maxRetryCount`, `nextRetryAt`, `lastAttemptedAt`, `retryable` 포함 여부 결정
- 프론트 알림 이력 화면에서 `RETRY_PENDING` 상태 표시
- 재시도 예정 시각과 재시도 횟수 표시

완료 조건:

- 사용자가 실패와 재시도 대기 상태를 구분할 수 있다.
- 원본 target은 계속 노출되지 않는다.
- 기존 알림 이력 화면 빌드가 통과한다.

## 12. 테스트 기준

Backend 테스트:

- retryable 실패 시 `RETRY_PENDING`으로 저장되는지 검증
- non-retryable 실패 시 `FAILED`로 저장되는지 검증
- 재시도 횟수가 남아 있으면 `nextRetryAt`이 설정되는지 검증
- 최대 재시도 초과 시 `FAILED`가 되는지 검증
- 재시도 성공 시 `SENT`가 되는지 검증
- disabled channel은 재시도하지 않고 최종 실패 처리되는지 검증

Frontend 테스트:

- `RETRY_PENDING` 상태가 알림 이력 화면에서 표시되는지 검증
- 재시도 횟수와 다음 재시도 예정 시각이 표시되는지 검증
- 원본 target이 노출되지 않는지 검증

수동 검증:

- Slack / Discord webhook 5xx 또는 timeout 상황을 테스트 double로 만들어 재시도 상태 전이를 확인한다.
- 잘못된 webhook URL 또는 404 응답은 즉시 최종 실패로 남는지 확인한다.

## 13. 다음 작업

Backend Issue 1인 `NotificationHistory 재시도 상태와 필드 추가`, Issue 2인 `알림 실패 분류 모델 추가`, Issue 3인 `자동 재시도 Scheduler 구현`, Issue 4인 `NotificationHistory 조회 응답 보완`은 완료됐다.

추가로 알림 중요도별 재시도 정책이 적용되어, 알림 이력 화면은 `FAILED + retryable=true + nextRetryAt` 항목을 최종 실패가 아닌 재시도 예정으로 표시한다.

알림 재시도 수동 재전송 정책 설계는 `docs/manual-notification-resend-policy.md`에 정리했다.

그 다음 순서는 `NotificationHistory 수동 재전송 메타 필드 추가`다.
