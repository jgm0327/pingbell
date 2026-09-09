# Pingbell 문제 해결 및 고민 정리

작성일: 2026-07-08

이 문서는 `docs/status`의 작업 상태 기록, 릴리즈 노트, README, 현재 코드 구조를 기준으로 지금까지 고민하고 해결한 문제를 정리한 문서다. 기능 나열보다 왜 그런 결정을 했고 어떤 문제를 줄였는지에 초점을 둔다.

## 1. 전체 흐름

Pingbell은 개인 개발자용 서버 헬스체크 및 장애 알림 서비스다. 지금까지의 작업 흐름은 다음 순서로 진행됐다.

1. 단일 Spring Boot 앱으로 MVP 1차 핵심 플로우를 완성했다.
2. EMAIL 알림에서 Slack / Discord webhook 알림으로 채널을 확장했다.
3. 알림 target 암호화, 마스킹, 이력 보존 등 운영 보안 문제를 해결했다.
4. 알림 실패 자동 재시도와 수동 재전송으로 장애 알림 신뢰성을 보강했다.
5. Kafka / Worker / DLQ를 바로 크게 도입하기보다 책임 경계와 실패 재처리 기준을 먼저 문서화했다.
6. 이후 Kafka consumer, DLQ command, 운영 문서, 알림 이력 UX를 작은 단위로 개선했다.

## 2. 고민 1: 모든 체크 실패를 바로 장애로 볼 것인가

초기 헬스체크 서비스에서 가장 먼저 정리해야 했던 문제는 "URL 호출 실패를 바로 장애로 판단할 것인가"였다. 네트워크는 순간적으로 흔들릴 수 있고, 1회 실패마다 알림을 보내면 사용자는 금방 알림 피로를 느낄 수 있다.

해결 방식:

- `CheckResult`와 `Incident`를 분리했다.
- URL 호출 결과는 매번 `CheckResult`로 저장한다.
- 연속 실패 횟수가 `failureThreshold` 이상일 때만 `Incident OPEN`으로 판단한다.
- 복구도 1회 성공이 아니라 연속 성공 횟수가 `recoveryThreshold` 이상일 때 처리한다.
- 이미 OPEN 상태의 incident가 있으면 같은 장애에 대해 중복 incident를 만들지 않는다.

관련 코드:

- `src/main/java/com/monit/pingbell/check/service/CheckService.java`
- `src/main/java/com/monit/pingbell/incident/service/IncidentDetectionService.java`

정리할 수 있는 포인트:

- 단순 요청 실패와 실제 장애를 분리했다.
- 운영 알림에서 중요한 것은 "실패 횟수"보다 "사용자가 조치해야 하는 장애 상태"라는 기준을 세웠다.

## 3. 고민 2: 헬스체크 결과 저장과 알림 발송 실패를 같이 롤백할 것인가

알림 발송은 SMTP, Slack, Discord 같은 외부 시스템에 의존한다. 외부 알림 발송이 실패했다고 해서 헬스체크 결과 저장이나 incident 판정까지 롤백되면 더 중요한 장애 기록이 사라질 수 있다.

해결 방식:

- incident 판정과 알림 발송의 실패 범위를 분리했다.
- 알림 발송 중 예외가 발생해도 헬스체크 결과와 incident 상태 전이는 유지한다.
- 알림 발송 성공/실패는 `NotificationHistory`에 별도 기록한다.
- retryable 실패는 `FAILED + retryable=true + nextRetryAt` 조합으로 다음 시도를 예약하고 scheduler가 재시도한다.
- 최종 실패한 알림은 사용자가 수동 재전송할 수 있게 했다.

관련 코드:

- `src/main/java/com/monit/pingbell/incident/service/IncidentDetectionService.java`
- `src/main/java/com/monit/pingbell/notification/service/NotificationRetryService.java`
- `src/main/java/com/monit/pingbell/notification/service/NotificationHistoryResendService.java`

정리할 수 있는 포인트:

- 핵심 도메인 기록과 외부 알림 실패를 분리해 장애 추적성을 보존했다.
- 알림 실패를 숨기지 않고 이력으로 남겨 운영자가 후속 조치를 할 수 있게 했다.

## 4. 고민 3: 알림 채널을 삭제하면 기존 이력은 어떻게 되는가

알림 채널을 hard delete하면 과거 `NotificationHistory`가 어떤 채널로 발송됐는지 추적하기 어려워진다. 특히 장애 알림 서비스에서 이력은 감사 로그 성격을 가진다.

해결 방식:

- 알림 채널 삭제는 DB row 삭제가 아니라 `enabled=false` 비활성화로 처리했다.
- 비활성화된 채널은 신규 알림 발송 대상에서 제외한다.
- 기존 알림 이력과 채널의 관계는 유지한다.
- 비활성 채널로 실패한 알림은 수동 재전송 불가 사유를 화면에 표시한다.

관련 코드:

- `src/main/java/com/monit/pingbell/notification/domain/NotificationChannel.java`
- `src/main/java/com/monit/pingbell/notification/service/NotificationChannelService.java`
- `frontend/src/pages/NotificationChannelPage.tsx`
- `frontend/src/widgets/NotificationHistoriesTable.tsx`

정리할 수 있는 포인트:

- 삭제 편의보다 운영 이력 보존을 우선했다.
- 사용자가 같은 `FAILED` 상태를 보더라도 채널 활성 여부에 따라 가능한 조치가 다르다는 점을 UI에 반영했다.

## 5. 고민 4: webhook URL과 email target을 어디까지 노출할 것인가

Slack / Discord webhook URL은 외부에서 메시지를 보낼 수 있는 secret이다. API 응답, 프론트 화면, 로그, DLQ payload에 원문이 남으면 보안 문제가 된다.

해결 방식:

- `notification_channels.target`은 DB 저장 시 암호화한다.
- 암호화 키는 환경변수 `NOTIFICATION_TARGET_ENCRYPTION_KEY`로 관리한다.
- API 응답에는 원본 target을 반환하지 않고 `maskedTarget`만 반환한다.
- 프론트 화면에서도 원본 target 대신 마스킹된 값만 보여준다.
- DLQ command의 reason 출력 직전에 URL, email, JWT, Bearer token, API key 후보 문자열을 redaction한다.

관련 코드:

- `src/main/java/com/monit/pingbell/notification/crypto/NotificationTargetCrypto.java`
- `src/main/java/com/monit/pingbell/notification/crypto/NotificationTargetEncryptConverter.java`
- `src/main/java/com/monit/pingbell/global/dlq/DlqReasonRedactor.java`

정리할 수 있는 포인트:

- 저장, 응답, 화면, 운영 로그라는 노출 경계를 각각 분리해 보안 정책을 적용했다.
- 기능 로직의 판단 값은 유지하면서, 출력 경계에서만 redaction을 적용해 회귀 위험을 줄였다.

## 6. 고민 5: 알림 실패를 어떻게 재시도할 것인가

알림 실패는 원인이 다양하다. 일시적인 네트워크 오류와 잘못된 webhook URL 같은 영구 실패를 같은 방식으로 재시도하면 불필요한 요청이 반복된다.

해결 방식:

- 실패 원인을 retryable / non-retryable로 분류했다.
- retryable 실패는 `FAILED + retryable=true + nextRetryAt` 조합으로 저장하고 `nextRetryAt` 기준으로 재시도한다.
- 최대 재시도 횟수를 초과하면 `FAILED`로 확정한다.
- non-retryable 실패는 바로 `FAILED`로 기록한다.
- 재시도 횟수와 다음 재시도 시각을 화면에 보여준다.

관련 코드:

- `src/main/java/com/monit/pingbell/notification/service/NotificationFailureClassifier.java`
- `src/main/java/com/monit/pingbell/notification/service/NotificationRetryService.java`
- `src/main/java/com/monit/pingbell/notification/scheduler/NotificationRetryScheduler.java`

정리할 수 있는 포인트:

- 무한 재시도를 막고, 운영자가 기다릴 실패와 조치해야 할 실패를 구분할 수 있게 했다.
- 재시도 상태 자체를 이력으로 남겨 장애 알림의 신뢰성을 추적 가능하게 만들었다.

## 7. 고민 6: 자동 재시도와 수동 재전송을 같은 이력으로 덮어쓸 것인가

실패한 알림을 수동으로 다시 보낼 때 원본 이력을 성공으로 덮어쓰면 실제 실패 기록이 사라진다. 반대로 새 이력을 만들지 않으면 사용자의 명시적 조치도 추적하기 어렵다.

해결 방식:

- 원본 실패 이력은 수정하지 않는다.
- 수동 재전송은 새로운 `NotificationHistory`를 생성한다.
- 새 이력에는 `manualResend=true`, `resendOfHistoryId=원본 ID`를 남긴다.
- 프론트에서는 재전송 성공/실패 메시지, 요청 중 버튼 상태, 새로 생성된 이력 강조 표시를 제공한다.

관련 코드:

- `src/main/java/com/monit/pingbell/notification/service/NotificationHistoryResendService.java`
- `frontend/src/pages/NotificationHistoryPage.tsx`
- `frontend/src/widgets/NotificationHistoriesTable.tsx`

정리할 수 있는 포인트:

- 감사 로그 성격의 알림 이력을 보존하면서 사용자 액션도 별도 이력으로 추적했다.
- 비동기 mutation 이후 사용자가 방금 만든 결과를 찾을 수 있도록 UX 피드백 루프를 개선했다.

## 8. 고민 7: 재시도 소진 실패를 별도 상태로 추가할 것인가

운영자는 단순 실패보다 "자동 재시도를 모두 소진한 실패"를 우선적으로 봐야 한다. 하지만 이를 위해 새 `NotificationStatus`를 추가하면 상태 모델과 기존 화면, API 계약이 커질 수 있다.

해결 방식:

- 새로운 status를 추가하지 않았다.
- 기존 `FAILED` 상태 안에서 `failureType=RETRY_EXHAUSTED` 조회 조건으로 구분했다.
- 백엔드는 `status=FAILED`와 `failureType=RETRY_EXHAUSTED` 조합만 허용한다.
- 프론트는 알림 이력 필터에 `재시도 소진 실패` 옵션을 추가하고 URL query string으로 상태를 복원한다.
- React Query `queryKey`에도 `failureType`을 포함해 필터별 캐시를 분리했다.

관련 코드:

- `src/main/java/com/monit/pingbell/notification/service/NotificationHistoryQueryService.java`
- `src/main/java/com/monit/pingbell/notification/repository/NotificationHistoryRepository.java`
- `frontend/src/features/notification-history/api.ts`
- `frontend/src/pages/NotificationHistoryPage.tsx`

정리할 수 있는 포인트:

- 상태 모델을 늘리지 않고 조회 조건만 확장해 변경 범위를 줄였다.
- 운영자가 조치해야 할 실패를 같은 이력 화면에서 빠르게 구분할 수 있게 했다.

## 9. 고민 8: Kafka, Worker, DLQ를 언제 도입할 것인가

처음부터 Kafka, 별도 Worker, DLQ, Kubernetes를 모두 구현하면 프로젝트가 복잡해지고 MVP 검증이 늦어진다. 반대로 아무 기준 없이 단일 앱만 만들면 이후 분리 시 책임 경계가 불명확해진다.

해결 방식:

- MVP 1차에서는 단일 Spring Boot 앱과 Docker Compose 로컬 실행을 우선했다.
- Kafka / Worker / DLQ는 먼저 문서로 책임 경계를 정리했다.
- Check Worker, Incident Detector, Notification Worker의 책임을 분리해 설계했다.
- 이후 Kafka mode와 consumer를 작은 단위로 구현했다.
- DLQ는 바로 운영 UI나 table을 만들지 않고, command 기반 list / dry-run / batch dry-run / reprocess 흐름으로 시작했다.

관련 문서:

- `docs/architecture/event-boundary.md`
- `docs/architecture/check-worker-design.md`
- `docs/architecture/incident-detector-design.md`
- `docs/architecture/notification-worker-design.md`
- `docs/dlq/README.md`
- `docs/dlq/operation-guide.md`

관련 코드:

- `src/main/java/com/monit/pingbell/check/scheduler/dispatch`
- `src/main/java/com/monit/pingbell/check/scheduler/event`
- `src/main/java/com/monit/pingbell/notification/event`
- `src/main/java/com/monit/pingbell/global/dlq`

정리할 수 있는 포인트:

- 고도화 기술 도입 자체보다 "어떤 실패를 재처리할 것인지", "어떤 payload를 남길 것인지", "중복 이벤트를 어떻게 막을 것인지"를 먼저 정의했다.
- DLQ 재처리는 실제 publish가 발생하므로 dry-run과 confirm 조건을 분리해 운영 실수 가능성을 낮췄다.

## 10. 고민 9: 중복 이벤트가 들어오면 incident가 중복 반영되지 않는가

Kafka 기반 비동기 흐름에서는 같은 이벤트가 여러 번 전달될 수 있다. 이때 같은 check result가 여러 번 incident 판정에 반영되면 failure count, recovery count, incident 생성, 알림 발송이 중복될 수 있다.

해결 방식:

- `HealthCheckCompletedEvent` 처리 시 payload를 그대로 믿지 않고 DB의 `CheckResult`를 다시 조회한다.
- 이벤트의 `monitorId`, `memberId`가 DB 관계와 일치하는지 검증한다.
- 처리한 `checkResultId`를 `incident_detection_processed_check_results`에 기록한다.
- 이미 처리한 check result는 duplicate event로 보고 skip한다.

관련 코드:

- `src/main/java/com/monit/pingbell/incident/service/IncidentDetectionService.java`
- `src/main/java/com/monit/pingbell/incident/domain/IncidentDetectionProcessedCheckResult.java`
- `src/main/java/com/monit/pingbell/incident/repository/IncidentDetectionProcessedCheckResultRepository.java`

정리할 수 있는 포인트:

- 이벤트 payload를 판단의 source of truth로 삼지 않고 DB row를 기준으로 판정했다.
- 비동기 환경의 at-least-once delivery를 전제로 idempotency 경계를 만들었다.

## 11. 고민 10: 운영 화면에서 상태값만 보여주면 충분한가

알림 이력에서 `FAILED`, `RETRY_PENDING`, `SENT` 같은 상태값만 보여주면 사용자가 다음에 무엇을 해야 하는지 알기 어렵다. 같은 `FAILED`라도 `retryable=true + nextRetryAt`이면 재시도 예정이고, 채널이 비활성화됐거나 재시도를 모두 소진했다면 운영자가 조치해야 하는 최종 실패다.

해결 방식:

- 상태 배지뿐 아니라 재전송 가능 여부, 채널 비활성 사유, 다음 재시도 시각을 함께 보여준다.
- 수동 재전송 요청 중인 행의 버튼만 disabled 처리한다.
- 재전송 성공 후 새 이력이 목록에 보이면 강조한다.
- 필터 때문에 새 이력이 보이지 않을 수 있으면 안내 문구를 보여준다.
- `재시도 소진 실패` 필터의 빈 상태 문구를 "조치 필요 실패가 없음"이라는 의미로 구체화했다.

관련 코드:

- `frontend/src/pages/NotificationHistoryPage.tsx`
- `frontend/src/widgets/NotificationHistoriesTable.tsx`
- `frontend/src/shared/components/StatusBadge.tsx`

정리할 수 있는 포인트:

- 운영 UI는 단순 데이터 표시가 아니라 다음 행동을 판단하게 해주는 화면이어야 한다는 기준으로 개선했다.
- API 계약 변경 없이 프론트 표현 계층에서 운영자 이해도를 높였다.

## 12. 반복적으로 적용한 개발 원칙

지금까지 작업에서 반복적으로 적용한 원칙은 다음과 같다.

- Entity를 API 응답으로 직접 반환하지 않고 DTO를 사용한다.
- secret은 코드에 하드코딩하지 않고 환경변수로 관리한다.
- 원본 webhook URL, email target은 API 응답과 화면에 노출하지 않는다.
- 문서만 수정한 작업은 Gradle 테스트를 실행하지 않는다.
- API 계약이 바뀌면 프론트 타입과 화면 영향을 함께 확인한다.
- 큰 고도화 작업은 문서로 책임 경계를 먼저 정리한 뒤 작은 구현 이슈로 나눈다.
- 사용자가 요청하지 않은 대규모 구조 변경은 하지 않는다.

## 13. 포트폴리오에 쓸 수 있는 요약

Pingbell 개발 과정에서 단순 CRUD보다 운영 상황에서 문제가 되는 지점을 먼저 정의하고 해결했다. 일시적 체크 실패와 실제 장애를 분리해 알림 피로를 줄였고, 알림 발송 실패가 헬스체크 결과 저장과 incident 판정을 롤백하지 않도록 실패 범위를 분리했다. Slack / Discord webhook URL은 secret으로 보고 DB 암호화, API 마스킹, 로그 redaction을 적용했다.

알림 실패는 retryable / non-retryable로 나누어 자동 재시도를 구현했고, 재시도 소진 후에는 수동 재전송과 이력 추적이 가능하도록 원본 이력을 보존하는 구조를 선택했다. Kafka와 DLQ는 처음부터 크게 도입하지 않고, Check Worker, Incident Detector, Notification Worker의 책임과 idempotency 기준을 먼저 문서화한 뒤 작은 단위로 구현했다.

이 과정에서 AI Agent를 단순 코드 생성 도구가 아니라, 프로젝트 원칙과 작업 범위를 지키는 협업 도구로 활용했다. `AGENTS.md`, 역할별 agent 문서, `docs/status` 기록을 통해 작업 기준을 유지했고, 구현 후에는 테스트 방법과 다음 작업을 문서로 남겨 이어서 개발할 수 있는 흐름을 만들었다.

## 14. 다음에 이어서 정리하면 좋은 항목

- 대시보드 알림 실패/재시도 카드에서 알림 이력 필터 링크 연결
- 알림 이력 필터 상태와 페이지 번호를 URL에 함께 유지할지 검토
- 재시도 소진 실패, 채널 비활성 실패, 일시 실패 등 운영자 안내 문구 표준화
- DLQ command 실제 실행 smoke test 결과 문서화
- 단일 앱에서 먼저 볼 최소 관측성 지표 구현 여부 결정

## 15. 고민 11: 체크 결과 요약 API와 skewed seed 기준 인덱스는 같이 봐야 하는가

모니터 상세 화면에서 24시간 trend와 failure summary를 보여주기 위해 check result summary API를 추가했다. 그런데 seed 데이터가 hot monitor에 치우치면 `check_results` 조회가 PK backward scan이나 `monitor_id` 단일 인덱스 filter에 의존해 느려질 수 있다. 그래서 skewed seed 기준으로 실제 쿼리 플랜과 실행 시간을 같이 봤다.

해결 방식:

- `idx_check_results_monitor_id_id_desc`로 monitor별 pagination을 11.230 ms -> 0.086 ms로 개선했다.
- `idx_check_results_monitor_created_at_desc`로 최신 1건 조회를 0.122 ms -> 0.051 ms로, 1시간 trend 조회를 1.151 ms -> 0.213 ms로 개선했다.
- 24시간 trend는 기존 `idx_check_results_monitor_id`와 차이가 거의 없어 추가 인덱스 이득이 없다고 판단했다.
- 인덱스 추가는 "한 번에 다 넣기"가 아니라 조회 패턴별 실측 결과를 보고 선택했다.

관련 문서:

- `docs/status/project-status-24.md`
- `docs/performance/check-result-index-performance-after-skewed-seed.md`

관련 코드:

- `src/main/java/com/monit/pingbell/check/controller/CheckResultController.java`
- `src/main/java/com/monit/pingbell/check/service/CheckResultQueryService.java`
- `src/main/java/com/monit/pingbell/check/repository/CheckResultRepository.java`
- `frontend/src/features/check-result/api.ts`
- `frontend/src/features/check-result/types.ts`
- `frontend/src/pages/MonitorDetailPage.tsx`

정리할 수 있는 포인트:

- 상세 화면의 trend/요약 API는 UX 기능이면서 동시에 조회 패턴을 바꾼다.
- skewed seed처럼 hot key 분포가 있으면 평균이 아니라 실제 분포와 최악 지점을 보고 인덱스를 검토해야 한다.
- 24시간 trend처럼 이득이 없는 쿼리는 기존 인덱스 유지가 더 낫다.
