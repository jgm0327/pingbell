# Pingbell 알림 수동 재전송 정책

작성일: 2026-06-22

## 1. 현재 판단

Pingbell은 자동 알림 재시도를 지원한다.

현재 자동 재시도 정책은 다음 흐름을 따른다.

- 최초 발송 실패가 retryable이면 `RETRY_PENDING`으로 기록한다.
- Scheduler가 `nextRetryAt` 이후 재발송한다.
- 재시도 성공 시 `SENT`로 변경한다.
- 최대 재시도 초과 또는 non-retryable 실패 시 `FAILED`로 최종 기록한다.

수동 재전송은 자동 재시도가 끝난 뒤 사용자가 실패 이력을 보고 직접 다시 보내는 기능이다. 이번 문서는 실제 구현이 아니라, 구현 전 정책과 이슈 단위를 정리하는 PM 산출물이다.

## 2. 작업 범위

- 자동 재시도 최종 실패 이후 수동 재전송 허용 여부 판단
- 기존 `NotificationHistory`를 수정할지 새 이력으로 남길지 결정
- 원본 이력과 수동 재전송 이력을 연결할 데이터 구조 결정
- target 수정 후 재전송 기준 결정
- 프론트 알림 이력 화면에서 제공할 액션 범위 결정
- Backend / Frontend 구현 이슈 분해

## 3. 제외 범위

- 실제 수동 재전송 구현
- Kafka / Worker 분리
- Slack / Discord OAuth
- DLQ 구현
- 사용자별 재시도 정책 커스터마이징
- 대량 재전송

## 4. 정책 결정

수동 재전송은 허용한다.

다만 기존 `NotificationHistory`를 덮어쓰지 않고, 새 `NotificationHistory`를 생성한다.

이유:

- 알림 발송 이력은 감사 로그 성격이 있으므로 과거 실패 기록을 보존해야 한다.
- 기존 실패 이력을 `SENT`로 바꾸면 실제 장애 대응 과정이 사라진다.
- 수동 재전송은 사용자의 명시적 액션이므로 자동 재시도와 별도 이력으로 남기는 것이 추적하기 쉽다.
- 면접/포트폴리오 관점에서도 “실패 이력 보존 + 재전송 이력 연결” 구조가 운영 안정성 설명에 적합하다.

## 5. 수동 재전송 허용 대상

수동 재전송은 다음 이력에만 허용한다.

- `FAILED` 상태인 알림 이력
- 알림 채널이 현재 enabled 상태인 이력
- 원본 incident와 monitor가 존재하는 이력
- 원본 notification type이 `INCIDENT_OPEN` 또는 `INCIDENT_RESOLVED`인 이력

권장 제한:

- `SENT` 이력은 재전송하지 않는다.
- `PENDING` 이력은 재전송하지 않는다.
- `RETRY_PENDING` 이력은 자동 재시도 대기 중이므로 재전송하지 않는다.
- disabled channel의 이력은 target 수정 후 채널을 재활성화한 뒤 새 이력 기준으로 재전송한다.

## 6. target 기준

수동 재전송은 원본 이력의 과거 target을 사용하지 않는다.

현재 `NotificationChannel`의 최신 target을 사용한다.

이유:

- webhook URL은 암호화되어 저장되고, 응답에서는 원본을 노출하지 않는다.
- 실패 원인이 잘못된 target이었다면 사용자는 채널 target을 수정한 뒤 재전송해야 한다.
- 과거 target으로 재전송하면 이미 잘못된 URL이나 폐기된 webhook을 다시 호출할 가능성이 높다.

## 7. 이력 연결 방식

새 수동 재전송 이력에는 원본 이력 ID를 연결한다.

권장 필드:

| 필드 | 타입 예시 | 설명 |
| --- | --- | --- |
| `resendOfHistoryId` | bigint nullable | 수동 재전송의 원본 NotificationHistory ID |
| `manualResend` | boolean | 사용자가 수동으로 만든 재전송 이력인지 여부 |

정책:

- 최초 자동 알림 이력은 `resendOfHistoryId = null`, `manualResend = false`다.
- 수동 재전송으로 생성된 새 이력은 `resendOfHistoryId = 원본 이력 id`, `manualResend = true`다.
- 수동 재전송 이력도 발송 실패 시 기존 자동 재시도 정책을 적용할 수 있다.
- 수동 재전송 이력의 실패가 retryable이면 `RETRY_PENDING`으로 들어간다.

## 8. API 설계

권장 API:

```text
POST /api/notification-histories/{historyId}/resend
```

동작:

1. 요청 사용자가 원본 history의 channel 소유자인지 확인한다.
2. 원본 history가 `FAILED`인지 확인한다.
3. 원본 history의 channel이 enabled인지 확인한다.
4. 원본 incident와 notification type으로 새 `NotificationHistory`를 생성한다.
5. 새 이력에 `manualResend=true`, `resendOfHistoryId=원본 id`를 저장한다.
6. 현재 channel target으로 알림을 발송한다.
7. 발송 성공 시 `SENT`, retryable 실패 시 `RETRY_PENDING`, non-retryable 실패 시 `FAILED`로 기록한다.

응답:

- 새로 생성된 `NotificationHistoryResponse`를 반환한다.
- 원본 target은 계속 노출하지 않고 `maskedTarget`만 반환한다.

## 9. 프론트 액션 정책

알림 이력 화면에서 `FAILED` 상태에만 `재전송` 버튼을 표시한다.

버튼 표시 조건:

- `status === "FAILED"`
- channel이 enabled인지 응답에서 확인 가능해야 한다.

프론트 응답 보완 필요:

| 필드 | 설명 |
| --- | --- |
| `channelEnabled` | 현재 알림 채널 활성 여부 |
| `manualResend` | 수동 재전송으로 생성된 이력 여부 |
| `resendOfHistoryId` | 원본 실패 이력 ID |

UI 동작:

- `FAILED` + channel enabled: `재전송` 버튼 표시
- `FAILED` + channel disabled: `채널 수정 후 재전송 가능` 문구 표시
- `RETRY_PENDING`: `자동 재시도 대기 중` 문구 표시, 수동 버튼 숨김
- `SENT`: 액션 없음

## 10. Backend 구현 이슈 분해

### Issue 1. NotificationHistory 수동 재전송 메타 필드 추가

담당 에이전트: Backend

작업 범위:

- `manualResend` 필드 추가
- `resendOfHistoryId` 필드 추가
- Flyway migration 작성
- `NotificationHistoryResponse`에 `manualResend`, `resendOfHistoryId`, `channelEnabled` 추가

완료 조건:

- 기존 알림 이력 조회가 유지된다.
- 수동 재전송 이력과 원본 이력을 연결할 수 있다.
- 원본 target은 계속 노출되지 않는다.

### Issue 2. 수동 재전송 API 구현

담당 에이전트: Backend

작업 범위:

- `POST /api/notification-histories/{historyId}/resend` 추가
- history 소유자 검증
- `FAILED` 상태만 재전송 허용
- enabled channel만 재전송 허용
- 새 `NotificationHistory` 생성 후 현재 channel target으로 발송
- 발송 결과에 따라 `SENT` / `RETRY_PENDING` / `FAILED` 기록

완료 조건:

- 원본 실패 이력을 덮어쓰지 않는다.
- 새 이력에 원본 이력 ID가 저장된다.
- retryable 실패는 기존 자동 재시도 정책에 연결된다.

### Issue 3. 프론트 알림 이력 재전송 액션 추가

담당 에이전트: Frontend

작업 범위:

- 알림 이력 타입에 `manualResend`, `resendOfHistoryId`, `channelEnabled` 추가
- `FAILED` + enabled channel인 경우 재전송 버튼 표시
- 재전송 API 호출
- 성공 시 알림 이력 목록 갱신
- disabled channel이면 안내 문구 표시

완료 조건:

- 사용자는 실패한 알림만 재전송할 수 있다.
- 자동 재시도 대기 중인 이력에는 수동 재전송 버튼이 보이지 않는다.
- 프론트 빌드가 통과한다.

## 11. 테스트 기준

Backend 테스트:

- `FAILED` 이력만 수동 재전송 가능한지 검증
- `SENT`, `PENDING`, `RETRY_PENDING` 이력은 재전송 거부되는지 검증
- 다른 사용자의 이력 재전송이 차단되는지 검증
- disabled channel 이력 재전송이 차단되는지 검증
- 수동 재전송 성공 시 새 `NotificationHistory`가 `SENT`로 생성되는지 검증
- 수동 재전송 retryable 실패 시 새 이력이 `RETRY_PENDING`으로 생성되는지 검증
- 원본 이력의 상태가 변경되지 않는지 검증

Frontend 테스트 / 빌드:

- `FAILED` + enabled channel에서 재전송 버튼 표시
- `RETRY_PENDING`에서 재전송 버튼 숨김
- disabled channel에서 안내 문구 표시
- `npm run build` 통과

수동 E2E:

- 테스트 webhook 서버를 404로 설정해 원본 이력을 `FAILED`로 만든다.
- channel target을 200 응답 가능한 webhook으로 수정한다.
- 알림 이력 화면에서 재전송을 실행한다.
- 새 이력이 `SENT`로 생성되고 원본 이력은 `FAILED`로 남는지 확인한다.

## 12. 다음 작업

다음 구현 작업은 Backend Issue 1인 `NotificationHistory 수동 재전송 메타 필드 추가`가 적절하다.

그 다음 순서는 `수동 재전송 API 구현`, `프론트 알림 이력 재전송 액션 추가` 순서로 진행한다.
