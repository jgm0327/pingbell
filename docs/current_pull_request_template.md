## 작업 내용

`docs/next_feature_request.md`의 "재시도 대기/초과 관측성 gauge·counter 추가"를 구현했다. `docs/operations/observability-metrics.md` 8.1절 후보(`retry_pending_current`, `retry_due_current`, `retry_exhausted_total`) 중 남아 있던 2개(`retry_due_current` gauge, `retry_exhausted_total` counter)를 마저 구현해, 이 절의 후보가 전부 끝났다.

## 변경 사항

- **`NotificationHistory.resolveFailureType()`(신규, 리팩터링)**: 기존에 `NotificationHistoryResponse`에만 private static으로 있던 failureType 판정 로직(미실패→null, 재시도예약→null, 채널비활성→CHANNEL_DISABLED, 재시도초과→RETRY_EXHAUSTED, 그 외→SEND_FAILED)을 도메인 객체로 옮겼다. 사용자에게 보여주는 `failureType`과 이번에 추가한 `retry.exhausted.total` counter가 같은 판정을 공유하게 하기 위해서다 — 로직을 두 곳에 복사했다면 나중에 둘이 어긋날 위험이 있었다.
- **`NotificationHistoryRepository`**: `countRetryDueHistories(now)` 추가 — 기존 `findRetryDueHistories`와 동일 조건(`retryable=true`, `nextRetryAt<=now`, `retryCount<maxRetryCount`)의 count 전용 쿼리, 전역(tenant 스코프 없음).
- **`PingbellMetrics`**: `pingbell.notification.retry.due.current`(gauge, 조회 시점마다 재계산) 추가, `recordNotificationRetryExhausted(channelType, notificationType)`(counter) 추가.
- **`NotificationRetryService`/`NotificationService`/`NotificationHistoryResendService`**: 각각 실패를 최종 확정하는 지점(기존 metrics 기록 직후) 딱 한 곳에 `history.resolveFailureType() == RETRY_EXHAUSTED`일 때만 counter를 증가시키는 코드를 추가했다.

## API 계약

- API 응답/계약 변경 없음. `NotificationHistoryResponse.failureType` 값은 이전과 동일한 로직으로 계산된다(위치만 옮김).
- `/actuator/metrics/pingbell.notification.retry.due.current`, `/actuator/metrics/pingbell.notification.retry.exhausted.total` 신규 조회 가능.

## 테스트 결과

- [x] `./gradlew.bat compileJava compileTestJava`
- [x] `NotificationHistoryTest`에 `resolveFailureType()` 5가지 분류 케이스 추가(미실패/재시도예약/채널비활성이 재시도초과보다 우선/재시도초과/일반실패).
- [x] `PingbellMetricsTest`에 `retry.due.current` gauge(재조회 시 재계산 확인) + `retry.exhausted.total` counter(태그별 정확한 집계) 케이스 추가.
- [x] `NotificationHistoryRepositoryTest`에 `countRetryDueHistories`가 여러 member에 걸쳐 전체 합산되는지, `nextRetryAt`이 아직 안 된 이력은 제외되는지 실제 DB로 확인.
- [x] `REDIS_PORT=<로컬 매핑 포트> ./gradlew.bat test` — 전체 테스트 스위트 통과(기존 회귀 없음, 기존 notification 관련 테스트도 재확인).

## 수동 테스트 방법

```powershell
$env:REDIS_PORT="<docker port pingbell-redis 로 확인한 포트>"
.\gradlew.bat test
```
앱 기동 후 `curl http://localhost:8080/actuator/metrics/pingbell.notification.retry.due.current`, `curl http://localhost:8080/actuator/metrics/pingbell.notification.retry.exhausted.total`로 조회.

## 고민한 점

- `retry_exhausted_total`을 어디서 증가시켜야 하는지 찾는 게 이번 작업의 핵심이었다. 재시도 소진은 `NotificationRetryService`(스케줄러 재시도), `NotificationService`(최초 발송 실패 후 즉시재시도), `NotificationHistoryResendService`(수동 재전송) 세 곳 모두에서 일어날 수 있어서, 각 서비스가 이미 갖고 있던 "실패 확정 후 metrics 기록" 지점에 조건부로 끼워 넣었다.
- 판정 조건을 서비스마다 다시 구현하지 않고 `NotificationHistory.resolveFailureType()`으로 옮겨 재사용했다 — `retryCount >= maxRetryCount`만 보면 안 되고(채널 비활성이 먼저 온 경우도 그 조건을 만족할 수 있음), 기존 DTO의 판정 순서(채널비활성 우선 확인)를 그대로 지켜야 해서 로직을 하나로 합치는 게 더 안전했다.

## Frontend 영향

해당 없음(Backend 전용 작업).

## 관련 이슈

close #
