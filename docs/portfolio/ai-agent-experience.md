# Pingbell 포트폴리오 정리: AI Agent 활용 개발 경험

작성일: 2026-06-23

## 1. 프로젝트 개요

Pingbell은 개인 개발자용 서버 헬스체크 및 장애 알림 서비스다.

사용자가 본인의 서버 URL을 등록하면 Spring Scheduler가 주기적으로 해당 URL을 호출하고, timeout, 4xx/5xx 응답, 요청 실패가 연속으로 발생했을 때 장애로 판단한다. 장애가 발생하거나 복구되면 사용자가 등록한 EMAIL, Slack, Discord 알림 채널로 알림을 발송한다.

현재 구현 범위는 MVP 2 이후 알림 신뢰성 개선 단계다.

주요 구현 기능:

- 회원가입 / 로그인
- JWT 인증
- 모니터링 URL 등록 / 조회 / 수정 / 삭제
- Scheduler 기반 헬스체크
- CheckResult 저장
- Incident 장애 발생 / 복구 판정
- EMAIL / Slack / Discord 알림 발송
- NotificationChannel 등록 / 조회 / 수정 / 비활성화 / 재활성화
- NotificationChannel target 암호화 및 마스킹
- NotificationHistory 조회
- 알림 발송 실패 자동 재시도
- 실패 알림 수동 재발송
- Kafka DLQ dry-run 검증 command
- Kafka DLQ 단일 record 수동 재처리 command
- 기본 대시보드 및 프론트 화면

## 2. AI Agent 활용 방식

이 프로젝트에서 AI Agent를 단순 코드 생성 도구가 아니라, 프로젝트 규칙을 따르는 개발 협업 도구로 활용했다.

프로젝트 루트에 `AGENTS.md`를 두고, AI Agent가 먼저 읽어야 할 공통 원칙을 정의했다. 또한 작업 성격에 따라 `docs/agents` 아래에 PM, Backend, Frontend, Infra Agent 문서를 분리했다. 이를 통해 AI가 매번 임의로 구현 방향을 정하지 않고, 현재 MVP 단계와 프로젝트 제약을 기준으로 작업하도록 했다.

AI Agent에게 고정한 주요 원칙:

- MVP를 먼저 완성한다.
- 초기 단계에서 Kafka, Kubernetes, MSA를 도입하지 않는다.
- 단일 Spring Boot 애플리케이션과 Docker Compose 기반 로컬 실행을 우선한다.
- 한 번에 하나의 명확한 작업만 수행한다.
- Entity를 API 응답으로 직접 반환하지 않는다.
- 환경변수와 secret을 코드에 하드코딩하지 않는다.
- 구현 결과에는 테스트 방법을 포함한다.

작업 흐름은 다음 방식으로 진행했다.

1. 요구사항이나 고민을 먼저 정책 문서로 정리한다.
2. AI Agent가 현재 코드 구조를 읽고 관련 파일을 파악한다.
3. 작업을 작은 단위로 나누어 구현한다.
4. 구현 후 테스트를 실행하고 실패 원인을 분리한다.
5. 결정 사항을 `docs/project-status.md`와 정책 문서에 남긴다.

이 방식 덕분에 새 세션에서도 `AGENTS.md`, 역할별 agent 문서, `docs/project-status.md`를 기준으로 현재 상태를 이어받을 수 있게 했다.

## 3. AI Agent와 함께 정리한 정책 문서

AI Agent를 활용해 기능 구현 전에 정책을 먼저 문서화했다.

작성한 주요 문서:

- `docs/project-status.md`
- `docs/policies/incident-alert-policy.md`
- `docs/policies/notification-retry-policy.md`
- `docs/policies/manual-notification-resend-policy.md`
- `docs/architecture/event-boundary.md`
- `docs/architecture/check-worker-design.md`
- `docs/architecture/incident-detector-design.md`
- `docs/architecture/notification-worker-design.md`
- `docs/dlq/README.md`
- `docs/operations/observability-metrics.md`
- `docs/release-notes/mvp-1.md`
- `docs/release-notes/mvp-2.md`

특히 알림 서비스는 단순히 "에러가 나면 전부 알림을 보낸다"로 구현하지 않고, 운영 관점에서 기준을 분리했다.

- 단일 체크 실패와 실제 장애를 구분한다.
- 연속 실패 기준을 넘을 때만 Incident를 생성한다.
- 이미 열린 Incident가 있으면 같은 장애에 대해 중복 알림을 보내지 않는다.
- 복구 기준을 만족했을 때만 복구 알림을 보낸다.
- 알림 발송 실패는 사용자 서버 장애 판단에 반영하지 않는다.
- 알림 실패는 별도 `NotificationHistory`로 추적한다.

또한 Kafka나 별도 Worker를 바로 구현하지 않고, 현재 단일 Spring Boot 구조에서 미래 비동기 분리를 위한 책임 경계를 먼저 문서화했다.

- Scheduler는 check 요청 생성 후보로 둔다.
- Check Worker는 URL 호출과 `CheckResult` 저장 후보로 둔다.
- Incident Detector는 monitor count 갱신, incident open / resolve, monitor 상태 전이 후보로 둔다.
- Notification Worker는 incident 이벤트 이후 채널별 알림 생성과 발송 후보로 둔다.
- Notification Sender는 `NotificationHistory` 기준으로 실제 EMAIL / Slack / Discord 발송과 실패 상태 반영을 맡는 후보로 둔다.
- 중복 이벤트가 들어와도 count, incident, notification이 중복 반영되지 않아야 한다는 idempotency 기준을 먼저 정리했다.
- webhook URL과 email address 같은 target 원문은 이벤트 payload, broker, DLQ에 남기지 않는 보안 기준을 함께 정리했다.
- DLQ / 재처리 정책에서는 retryable, non-retryable, retry exhausted를 구분해 자동 재처리와 운영자 수동 재처리 대상을 분리했다.
- DLQ dry-run command에서는 실제 재처리 전에 payload와 DB 관계, idempotency 기준을 확인해 재처리 가능 여부를 먼저 판정하도록 했다.
- DLQ 실제 재처리 command에서는 service 직접 호출 대신 source topic re-publish를 사용해 기존 Kafka consumer 흐름과 idempotency 경계를 재사용하도록 했다.
- 관측성 지표 설계에서는 check failure와 incident open, notification failure, retry exhausted, DLQ 후보를 분리해 운영자가 원인을 구분할 수 있도록 했다.
- metric label에는 URL, webhook, email address, raw error message를 넣지 않는 기준을 세워 cardinality와 secret 노출 위험을 줄였다.

중요한 점은 이 설계 문서들이 현재 구현과 미래 확장을 구분한다는 것이다. 현재 런타임은 단일 Spring Boot 앱이며, Kafka, 별도 Worker, DLQ, Prometheus / Grafana는 아직 구현하지 않았다. 대신 실제로 도입하기 전에 어떤 책임을 어디로 분리할지, 어떤 실패를 재처리할지, 어떤 지표를 볼지 먼저 고정했다.

## 4. 구현 기능과 기술적 고려

### 4.1 헬스체크와 장애 판정

`CheckService`는 `ACTIVE` 또는 `DOWN` 상태이며 `nextCheckAt`이 지난 monitor를 조회해 헬스체크를 수행한다.

헬스체크 결과는 `CheckResult`로 저장하고, 결과에 따라 monitor의 연속 성공/실패 횟수를 갱신한다.

장애 판단 기준:

- 1xx / 2xx / 3xx: 성공
- 4xx / 5xx: 실패
- timeout: timeout 실패
- 연속 실패 횟수 >= `failureThreshold`: Incident OPEN
- 연속 성공 횟수 >= `recoveryThreshold`: Incident RESOLVED

고려한 점:

- 한 번 실패했다고 바로 장애로 판단하지 않았다.
- 이미 OPEN incident가 있으면 중복 생성하지 않았다.
- 장애 발생 알림과 복구 알림을 분리했다.
- 알림 발송 실패가 헬스체크 저장과 장애 판정을 롤백하지 않도록 했다.

### 4.2 알림 채널 확장

MVP 1에서는 EMAIL 알림을 구현했고, MVP 2에서는 Slack / Discord webhook 알림 채널을 확장했다.

지원 채널:

- EMAIL
- SLACK webhook
- DISCORD webhook

고려한 점:

- 채널별 sender를 분리해 확장 가능하게 구성했다.
- 채널 삭제는 hard delete가 아니라 `enabled=false`로 처리했다.
- 기존 알림 이력과 채널 관계를 보존했다.
- 알림 채널이 없으면 회원가입 이메일을 기본 EMAIL 채널로 활용한다.

### 4.3 알림 target 보안

Slack / Discord webhook URL은 외부에서 메시지를 보낼 수 있는 민감 정보이므로 secret으로 취급했다.

구현한 보안 정책:

- `notification_channels.target`은 DB 저장 시 암호화한다.
- 암호화 키는 `NOTIFICATION_TARGET_ENCRYPTION_KEY` 환경변수로 관리한다.
- API 응답에는 원본 target을 반환하지 않고 `maskedTarget`만 반환한다.
- 알림 이력의 실패 메시지에 URL이 포함될 경우 `[redacted-url]`로 치환한다.
- target 수정 / 재활성화 시에는 새 target을 다시 입력하게 했다.

이 부분은 포트폴리오에서 보안 고려 경험으로 설명할 수 있다.

### 4.4 알림 실패 자동 재시도

알림 발송은 네트워크나 외부 서비스 상태에 따라 일시적으로 실패할 수 있다. 그래서 실패 원인을 retryable / non-retryable로 분류했다.

retryable로 본 경우:

- Slack / Discord webhook timeout
- Slack / Discord webhook 5xx
- Slack / Discord webhook 429
- SMTP 계열 일시 실패
- 네트워크 접근 실패

non-retryable로 본 경우:

- 잘못된 target 형식
- 인증 실패
- 권한 문제
- 복호화 실패
- 지원하지 않는 채널 타입

재시도 정책:

- 최초 실패 후 1분 뒤 1차 재시도
- 1차 재시도 실패 후 5분 뒤 2차 재시도
- 최대 재시도 횟수 초과 시 최종 FAILED
- 재시도 대상은 `RETRY_PENDING` 상태와 `nextRetryAt` 기준으로 조회

고려한 점:

- 무한 재시도를 막았다.
- 실패 상태와 다음 재시도 시각을 이력으로 남겼다.
- 알림 재시도 실패가 health check / incident 로직에 영향을 주지 않도록 분리했다.

### 4.5 실패 알림 수동 재발송

자동 재시도 후에도 실패한 알림은 사용자가 직접 재발송할 수 있도록 설계했다.

정책:

- `FAILED` 상태의 알림 이력만 수동 재발송 가능
- `SENT`, `PENDING`, `RETRY_PENDING` 상태는 수동 재발송 불가
- 비활성화된 채널은 재발송 불가
- 원본 `NotificationHistory`는 수정하지 않고 새 이력을 생성
- 새 이력에는 `manualResend=true`, `resendOfHistoryId=원본 ID`를 저장

고려한 점:

- 감사 로그 성격의 알림 이력을 보존했다.
- 원본 실패 이력을 `SENT`로 덮어쓰지 않았다.
- 사용자의 명시적 재발송 액션과 자동 재시도를 구분했다.

### 4.6 프론트엔드 화면

프론트엔드는 Vite, React, TypeScript 기반으로 구성했다.

구현 화면:

- 로그인 / 회원가입
- 대시보드
- 모니터 목록
- 모니터 등록 / 수정
- 모니터 상세
- 체크 결과 목록
- 장애 이력 목록
- 알림 채널 관리
- 알림 이력 조회

알림 이력 화면에서는 다음 정보를 보여준다.

- 생성일
- 모니터명
- 채널 타입
- 마스킹된 target
- 알림 타입
- 발송 상태
- 재시도 횟수
- 다음 재시도 시각
- 마지막 시도 시각
- 실패 사유
- 수동 재전송 버튼

고려한 점:

- `RETRY_PENDING` 상태에서는 수동 재전송 대신 자동 재시도 대기 상태를 보여준다.
- `FAILED`이고 채널이 활성화된 경우에만 재전송 버튼을 노출한다.
- 채널이 비활성화된 경우 "채널 수정 후 재전송 가능"으로 안내한다.

## 5. AI Agent 활용으로 얻은 효과

AI Agent를 활용하면서 가장 효과가 컸던 부분은 반복 구현보다 프로젝트 기준을 유지하는 일이었다.

효과:

- 기능을 MVP 단위로 작게 나누어 구현할 수 있었다.
- 정책 문서를 먼저 작성해 구현 범위가 흔들리지 않았다.
- 알림 재시도, 수동 재발송, 장애 알림 기준처럼 운영 정책이 필요한 기능을 코드로 바로 들어가지 않고 문서로 먼저 정리했다.
- 코드 변경 후 테스트 방법과 실패 원인을 함께 확인했다.
- 새 세션에서도 `docs/project-status.md`를 기준으로 이어서 개발할 수 있게 했다.

단순히 AI가 작성한 코드를 그대로 사용한 것이 아니라, 내가 요구사항, 제약, 우선순위, 금지 사항을 정의하고 AI가 제안한 구현을 검토하면서 개발했다.

## 6. 문제 해결 경험

### 문제 1. 모든 실패를 알림으로 보내면 알림 피로가 커질 수 있음

처음에는 서버 요청 실패를 모두 알림으로 보낼 수도 있었지만, 이 방식은 작은 네트워크 흔들림에도 알림이 과도하게 발생할 수 있다.

해결:

- Check Failure와 Incident를 분리했다.
- `failureThreshold` 이상 연속 실패한 경우에만 장애 알림을 보낸다.
- 이미 열린 incident가 있으면 중복 OPEN 알림을 보내지 않는다.
- 복구도 `recoveryThreshold` 이상 연속 성공한 경우에만 처리한다.

### 문제 2. 알림 발송 실패가 장애 판정을 방해할 수 있음

알림 발송은 외부 SMTP, Slack, Discord에 의존한다. 이 과정에서 실패가 발생해도 서버 헬스체크 결과 저장이나 incident 생성이 롤백되면 안 된다.

해결:

- 알림 발송 실패를 try-catch로 격리했다.
- `NotificationHistory`에 실패 상태를 저장했다.
- 알림 재시도는 별도 Scheduler와 Service로 분리했다.
- `CheckServiceTest`로 알림 실패가 헬스체크 흐름을 막지 않는지 검증했다.

### 문제 3. webhook URL이 API 응답이나 에러 메시지로 노출될 수 있음

Slack / Discord webhook URL은 외부 메시지 발송 권한을 가진 secret이다.

해결:

- DB 저장 시 target 암호화
- API 응답에서 원본 target 제거
- 마스킹된 target만 반환
- 에러 메시지 내 URL redaction 처리
- `.env` 기반 환경변수 사용

## 7. 테스트와 검증

주요 테스트 범위:

- 회원가입 / 로그인
- Monitor 등록 / 수정
- 헬스체크 결과 저장
- 장애 발생 판정
- 장애 복구 판정
- 이미 OPEN incident가 있을 때 중복 생성 방지
- 알림 발송 이력 저장
- 알림 재시도 상태 전이
- 수동 재발송 상태 전이
- Slack / Discord sender payload
- Notification target 암호화 / 복호화

최근 확인한 테스트:

```powershell
.\gradlew.bat test --tests com.monit.pingbell.check.service.CheckServiceTest
```

전체 테스트는 로컬 PostgreSQL 컨테이너가 실행 중일 때 다음 명령으로 확인한다.

```powershell
.\gradlew.bat test
```

## 8. 포트폴리오용 요약 문장

Pingbell은 개인 개발자용 서버 헬스체크 및 장애 알림 서비스로, 사용자가 등록한 URL을 주기적으로 호출해 장애 발생과 복구를 감지하고 EMAIL, Slack, Discord로 알림을 발송하는 프로젝트입니다. 저는 이 프로젝트에서 AI Agent를 단순 코드 생성 도구가 아니라 개발 프로세스에 통합된 협업 도구로 활용했습니다. `AGENTS.md`와 역할별 agent 문서를 작성해 MVP 범위, 금지 사항, 구현 규칙, 테스트 기준을 고정했고, AI Agent가 해당 기준을 따르며 코드를 분석하고 기능을 구현하도록 했습니다.

주요 기능으로는 JWT 인증, 모니터링 URL 관리, Scheduler 기반 헬스체크, CheckResult 저장, Incident 장애 발생/복구 판정, EMAIL/Slack/Discord 알림, 알림 채널 target 암호화, 알림 이력 조회, 알림 실패 자동 재시도, 실패 알림 수동 재발송을 구현했습니다. 특히 모든 실패를 즉시 알림으로 보내지 않고, 연속 실패 기준을 넘었을 때만 장애로 판단하도록 설계했습니다. 또한 같은 incident에 대한 중복 알림을 방지하고, 알림 발송 실패가 헬스체크 결과 저장이나 장애 판정을 롤백하지 않도록 분리했습니다.

AI Agent를 활용하면서 기능 구현 전 정책 문서를 먼저 작성하고, 구현 후 테스트와 문서 동기화를 반복했습니다. 이를 통해 알림 재시도 정책, 수동 재발송 정책, 장애 알림 기준처럼 운영 관점의 의사결정을 코드에 반영할 수 있었습니다. 이후에는 Kafka나 Worker를 바로 붙이지 않고, Check Worker, Incident Detector, Notification Worker, DLQ, 관측성 지표를 문서로 먼저 설계해 현재 구현과 미래 확장 범위를 분리했습니다. 이 경험을 통해 AI를 효과적으로 활용하려면 단순히 질문하는 것보다 프로젝트 목표, 제약 조건, 품질 기준, 검증 방법을 명확히 제공하는 것이 중요하다는 점을 배웠습니다.

## 9. 면접에서 강조할 포인트

- AI Agent에게 구현을 전부 맡긴 것이 아니라, 직접 프로젝트 원칙과 경계를 정의했다.
- MVP 단계에서는 Kafka, Kubernetes, MSA를 의도적으로 제외하고 단일 Spring Boot 구조로 핵심 기능 검증에 집중했다.
- Kafka / Worker / DLQ / Prometheus는 구현된 기능이 아니라 운영 설계 문서로 먼저 분리했다.
- 장애 알림 서비스 특성상 알림 피로, 중복 알림, retryable 실패, secret 노출을 고려했다.
- 알림 발송 실패가 핵심 헬스체크 로직에 영향을 주지 않도록 실패 범위를 분리했다.
- webhook URL 암호화, 마스킹, 에러 메시지 redaction으로 보안성을 고려했다.
- 관측성 지표 설계에서 check failure, incident open, notification failure를 분리해 장애 원인을 구분하려 했다.
- 정책 문서와 테스트를 함께 남겨 새 세션이나 다른 AI 도구에서도 이어서 작업할 수 있게 했다.

## 10. 앞으로 개선할 점

- 알림 이력 화면의 상태 표시 UX 개선
- slow response 기록 정책 도입 여부 결정
- 반복 알림 cooldown과 flapping 방지 정책 검토
- 사용자별 알림 민감도 설정
- 운영 설계 문서를 바탕으로 실제 구현 우선순위 재정리
- 단일 앱에서 먼저 볼 최소 관측성 지표 구현 검토
- 추후 Kafka 기반 Worker 분리 도입 시점 판단
