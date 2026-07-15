# Pingbell

Pingbell은 개인 개발자용 서버 헬스체크 및 장애 알림 서비스다.

사용자가 본인의 서버 URL을 등록하면 Spring 스케줄러가 주기적으로 URL을 호출하고, 실패, 시간 초과, 4xx/5xx 응답, 응답 지연이 연속으로 발생하면 장애로 판단한다. 장애가 발생하거나 복구되면 등록된 알림 채널로 알림을 발송한다.

## 현재 상태

현재 코드는 **MVP 1차 완료 후 MVP 2 알림 채널 확장까지 구현된 상태**다.

- MVP 1: 서버 헬스체크, 장애 발생 / 복구 판정, 이메일 알림, 기본 프론트 화면
- MVP 2: Slack / Discord 웹훅 알림 채널 확장

릴리즈 노트:

- `docs/release-notes/mvp-1.md`
- `docs/release-notes/mvp-2.md`

운영 설계 문서:

아래 문서는 Kafka 모드, 작업자 분리, DLQ, 관측성 도입 전후의 책임 경계와 운영 기준을 정리한 문서다. 현재 실행 환경에는 단일 Spring Boot 앱 안의 Kafka 모드와 최소 DLQ 토픽 경계가 있으며, 별도 작업자 애플리케이션과 Prometheus / Grafana는 아직 구현하지 않았다.

- `docs/event-boundary.md`: 단일 앱 안의 현재 동기 흐름과 미래 이벤트 경계
- `docs/check-worker-design.md`: 체크 작업자 분리 시 책임과 멱등성 기준
- `docs/incident-detector-design.md`: 장애 감지기 분리 시 장애 판정과 상태 전이 기준
- `docs/notification-worker-design.md`: 알림 작업자 분리 시 알림 이력과 발송 요청 기준
- `docs/dlq-reprocessing-policy.md`: DLQ 수동 확인 절차와 재시도 가능 / 재시도 불가 / 재시도 소진, 재처리·폐기 기준
- `docs/observability-metrics.md`: 현재 단일 앱과 미래 작업자 구조에서 볼 관측성 지표 후보

## 주요 기능

### 인증

- 회원가입
- 로그인
- JWT 액세스 토큰 발급
- 회원가입 시 가입 이메일을 기본 이메일 알림 채널로 자동 생성

### 모니터

- 모니터링 URL 등록
- 모니터링 URL 목록 / 상세 조회
- 모니터링 URL 수정
- 모니터 일시정지 / 재활성화
- 모니터 논리 삭제
- 실행 주기 / 시간 초과 / 실패 임계치 / 복구 임계치 설정

### 헬스 체크

- 스케줄러가 `nextCheckAt`이 지난 `ACTIVE` / `DOWN` 모니터를 조회
- URL 호출 결과를 `CheckResult`로 저장
- 1xx / 2xx / 3xx는 성공으로 판단
- 4xx / 5xx는 `HTTP_ERROR`로 저장
- 시간 초과는 `TIMEOUT`으로 저장
- 응답 시간이 모니터의 `timeoutMillis`를 넘으면 `SLOW_RESPONSE`로 저장
- 기타 호출 실패는 `FAILURE`로 저장

### 장애

- 연속 실패 횟수가 `failureThreshold` 이상이면 `OPEN` 장애 생성
- 이미 `OPEN` 장애가 있으면 중복 생성하지 않음
- 연속 성공 횟수가 `recoveryThreshold` 이상이면 `RESOLVED` 처리
- 장애 발생 시 모니터 상태를 `DOWN`으로 변경
- 장애 복구 시 모니터 상태를 `ACTIVE`로 변경

### 알림

지원 알림 채널:

- 이메일
- Slack 웹훅
- Discord 웹훅

알림 동작:

- 장애 발생 시 `INCIDENT_OPEN` 알림 발송
- 장애 복구 시 `INCIDENT_RESOLVED` 알림 발송
- 활성화된 알림 채널별로 발송
- 발송 성공 / 실패를 `NotificationHistory`에 기록
- 재시도 가능한 실패는 `RETRY_PENDING`으로 기록하고 스케줄러가 재시도
- 재시도 성공 시 `SENT`, 최대 재시도 초과 또는 재시도 불가능한 실패 시 `FAILED`로 기록
- 최종 실패한 알림 이력은 사용자가 수동 재전송 가능
- 알림 이력 화면에서 채널별 발송 결과와 실패 사유 확인
- 알림 발송 실패가 체크 결과 저장이나 장애 판정을 롤백하지 않음

### 알림 채널

알림 채널 API와 프론트 화면은 다음 기능을 지원한다.

- 이메일 / Slack / Discord 채널 등록
- 채널 목록 조회
- 대상 수정
- 채널 비활성화
- 비활성화된 채널 재활성화

대상 형식:

- 이메일: 이메일 주소
- Slack: Slack 수신 웹훅 URL
- Discord: Discord 웹훅 URL

보안 정책:

- 웹훅 URL은 비밀 정보로 취급한다.
- `notification_channels.target`은 DB 저장 시 암호화된다.
- 암호화 키는 `NOTIFICATION_TARGET_ENCRYPTION_KEY` 환경변수로 설정한다.
- 기존 평문 대상은 앱 시작 시 데이터 보정 작업으로 암호화한다.
- 기존 평문 대상은 읽기 호환도 유지한다.
- 알림 채널 조회 API는 원본 대상을 반환하지 않고 `maskedTarget`만 반환한다.
- 프론트 목록 화면은 마스킹된 대상만 보여준다.
- 대상 수정 / 재활성화 시에는 새 대상을 다시 입력해야 한다.
- 실제 발송에는 서버에 저장된 대상을 사용한다.

삭제 정책:

- 채널 삭제는 DB 행의 물리 삭제가 아니다.
- `enabled=false` 비활성화로 처리한다.
- 기존 알림 이력과의 관계를 보존하기 위한 정책이다.

## 기술 스택

백엔드:

- Java 21
- Spring Boot 3.5
- Spring Web
- Spring Security
- JWT
- Spring Data JPA
- Flyway
- PostgreSQL
- Spring Scheduler
- Spring Mail

프론트엔드:

- Vite
- React
- TypeScript
- React Router
- TanStack Query
- React Hook Form

로컬 인프라:

- Docker Compose
- PostgreSQL 16
- Mailpit
- Redis 7.4
- Kafka 3.9(KRaft 단일 브로커)

## 프로젝트 구조

```text
pingbell
├─ src/main/java/com/monit/pingbell
│  ├─ auth
│  ├─ check
│  ├─ global
│  ├─ incident
│  ├─ member
│  ├─ monitor
│  └─ notification
├─ src/main/resources
│  ├─ application.yml
│  └─ db/migration
├─ frontend
│  ├─ src/app
│  ├─ src/features
│  ├─ src/pages
│  ├─ src/shared
│  └─ src/widgets
└─ docker-compose.yml
```

## 로컬 실행

### 1. 환경 변수 파일 생성

프로젝트 루트에 `.env` 파일을 만든다.

Windows PowerShell 환경:

```powershell
copy .env.example .env
```

macOS / Linux 환경:

```bash
cp .env.example .env
```

기본 로컬 예시는 다음과 같다.

```env
POSTGRES_DB=pingbell
POSTGRES_USER=pingbell
POSTGRES_PASSWORD=pingbell1234!!
POSTGRES_PORT=5432

## HealthCheckCompleted 장애 감지 소비자

`kafka` 분배 모드에서 요청된 체크의 소비자가 `CheckResult`를 저장하면 Pingbell은
`pingbell.health-check.completed` 이벤트를 소비한다.

완료 이벤트 소비자는 `checkResultId`로 `CheckResult`를 다시 조회하고, 해당 DB 행을
장애 발생·복구 판정의 기준 데이터로 사용한다. `status`, `httpStatus`,
`responseTimeMs`, `errorMessage` 같은 페이로드 필드는 장애 판정의 기준이 아닌
이벤트 메타데이터로 취급한다. 결과를 적용하기 전에 소비자는 이벤트의 `monitorId`와
`memberId`가 다시 조회한 `CheckResult`의 관계 정보와 일치하는지 검증한다.

처리한 `checkResultId`를 `incident_detection_processed_check_results`에 기록해
중복 완료 이벤트를 건너뛴다. 동일한 완료 이벤트가 모니터의 실패·복구 횟수를
두 번 이상 증가시켜서는 안 된다.

수동 확인:

```powershell
docker compose up -d kafka
.\gradlew.bat bootRun --args="--pingbell.check.dispatch-mode=kafka"
```

실행 시점이 된 모니터를 생성하거나 기다린 후 다음 흐름을 확인한다.

```text
HealthCheckRequested
-> URL 체크 / CheckResult 저장
-> HealthCheckCompleted
-> 장애 판정
```

동일한 `checkResultId`를 가진 완료 이벤트를 다시 처리해도 장애 상태나 모니터의
실패·복구 횟수가 다시 변경되지 않아야 한다.
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=

JWT_SECRET=change-this-to-a-long-random-secret
JWT_REFRESH_TOKEN_EXPIRATION_MS=1209600000
JWT_REMEMBER_ME_REFRESH_TOKEN_EXPIRATION_MS=2592000000
NOTIFICATION_TARGET_ENCRYPTION_KEY=change-this-to-a-long-random-notification-target-key

MAIL_HOST=localhost
MAIL_PORT=1025
MAIL_USERNAME=
MAIL_PASSWORD=
MAIL_SMTP_AUTH=false
MAIL_SMTP_STARTTLS_ENABLE=false

MAILPIT_SMTP_PORT=1025
MAILPIT_WEB_PORT=8025

KAFKA_PORT=9092
KAFKA_BOOTSTRAP_SERVERS=localhost:9092
PINGBELL_CHECK_DISPATCH_MODE=direct
PINGBELL_KAFKA_CONSUMER_GROUP_ID=pingbell-check-worker
PINGBELL_NOTIFICATION_WORKER_GROUP_ID=pingbell-notification-worker
PINGBELL_KAFKA_TOPIC_HEALTH_CHECK_REQUESTED=pingbell.health-check.requested
PINGBELL_KAFKA_TOPIC_HEALTH_CHECK_COMPLETED=pingbell.health-check.completed
PINGBELL_KAFKA_TOPIC_NOTIFICATION_REQUESTED=pingbell.notification.requested
```

로컬 이메일 검증은 Mailpit 사용을 기본으로 한다. 실제 Gmail SMTP 등을 사용하려면 `.env`의 `MAIL_*` 값을 실제 SMTP 설정으로 바꾼다.

Slack / Discord 알림은 애플리케이션 실행 후 알림 채널 화면에서 각 서비스의 웹훅 URL을 등록해 사용한다.

### 2. 로컬 인프라 실행

```bash
docker compose up -d
```

컨테이너 확인:

```bash
docker ps --filter name=pingbell
```

접속 정보:

- PostgreSQL: `localhost:5432`
- Redis: `localhost:6379`
- Mailpit SMTP: `localhost:1025`
- Mailpit 웹 화면: `http://localhost:8025`
- Kafka: `localhost:9092`

### Kafka 분배 모드

기본값은 기존 동기 헬스체크 흐름을 유지하는 `direct` 모드다.

```env
PINGBELL_CHECK_DISPATCH_MODE=direct
```

Kafka 이벤트 발행 경로를 확인하려면 Kafka가 실행 중인 상태에서 다음처럼 실행한다.

```powershell
.\gradlew.bat bootRun --args="--pingbell.check.dispatch-mode=kafka"
```

`kafka` 모드에서는 스케줄러가 실행 시점이 된 모니터를 조회해 `pingbell.health-check.requested` 토픽으로 `HealthCheckRequested` 이벤트를 발행한다. 같은 애플리케이션 안의 소비자가 이벤트를 받아 DB에서 모니터의 최신 상태를 다시 조회한 뒤, 기존 `direct` 모드와 같은 체크 수행, `CheckResult` 저장, 장애 판정, 알림 발송, `nextCheckAt` 갱신 흐름을 실행한다.

소비자 그룹 ID는 다음 환경변수로 변경할 수 있다.

```env
PINGBELL_KAFKA_CONSUMER_GROUP_ID=pingbell-check-worker
```

`HealthCheckRequested` 소비자가 체크 결과 저장까지 완료하면 `pingbell.health-check.completed` 토픽으로 `HealthCheckCompleted` 이벤트를 발행한다. 완료 이벤트 페이로드에는 `eventId`, `requestEventId`, `monitorId`, `memberId`, `checkResultId`, `status`, `httpStatus`, `responseTimeMs`, `errorMessage`, `checkedAt`만 포함한다. 모니터 URL, 이메일, 알림 대상, 비밀 정보는 Kafka 페이로드에 넣지 않는다.

완료 이벤트 토픽 이름은 다음 환경변수로 바꿀 수 있다.

```env
PINGBELL_KAFKA_TOPIC_HEALTH_CHECK_COMPLETED=pingbell.health-check.completed
```

완료 이벤트 발행 실패 시 소비자는 예외를 다시 던진다. 다만 체크 결과 저장과 `nextCheckAt` 갱신은 `CheckService` 트랜잭션에서 먼저 완료되므로 완료 이벤트 발행 실패만으로 자동 롤백되지 않는다.

### 알림 작업자 소비자

`kafka` 분배 모드에서는 장애 발생·복구 시 장애 판정 영역에서 발송기를 직접 호출하지
않는다. 완료 이벤트 소비자가 장애 판정을 적용한 후 `NotificationRequested` 이벤트를
`pingbell.notification.requested` 토픽으로 발행한다.

알림 소비자는 `incidentId`로 데이터베이스에서 `Incident`를 다시 조회한 후 기존
`NotificationService`를 사용한다. `Incident`, `NotificationChannel`,
`NotificationHistory`의 기준 데이터는 계속 데이터베이스에 둔다. Kafka 페이로드에는
알림 대상, 웹훅 URL, 이메일 주소, 비밀 정보를 포함하지 않는다.

중복 알림 발송은 계속
`NotificationHistoryRepository.existsByIncidentAndChannelAndNotificationType`로
방지한다. 동일한 장애, 채널, 알림 유형에 두 번째 `NotificationRequested` 이벤트가
들어와도 알림을 새로 생성하거나 발송하지 않는다.

알림 작업자 설정:

```env
PINGBELL_NOTIFICATION_WORKER_GROUP_ID=pingbell-notification-worker
PINGBELL_KAFKA_TOPIC_NOTIFICATION_REQUESTED=pingbell.notification.requested
```

### Kafka 소비자 재시도와 DLQ

`kafka` 분배 모드에서는 모든 Kafka 리스너가 공통 Spring Kafka 오류 처리기를 사용한다.

- 재시도 가능한 실패는 고정 대기 시간을 두고 재시도한다.
- 재시도 불가능한 실패는 즉시 DLQ로 보낸다.
- 재시도 횟수를 소진한 메시지는 DLQ로 보낸다.
- DLQ 토픽 이름은 원본 토픽 이름 뒤에 `.dlq`를 붙인다.

기본 설정:

```env
PINGBELL_KAFKA_CONSUMER_RETRY_BACKOFF_INTERVAL_MS=1000
PINGBELL_KAFKA_CONSUMER_RETRY_MAX_RETRIES=2
```

앱이 `kafka` 모드에서 생성하는 DLQ 토픽:

```text
pingbell.health-check.requested.dlq
pingbell.health-check.completed.dlq
pingbell.notification.requested.dlq
```

현재 재시도하지 않는 예외:

```text
IllegalArgumentException
ClassCastException
NoSuchElementException
```

DLQ 레코드는 원본 이벤트 페이로드를 유지한다. Kafka와 DLQ 페이로드에는 모니터 URL,
알림 대상, 웹훅 URL, 이메일 주소, 비밀 정보를 포함해서는 안 된다. DLQ 복구기는 원본
토픽, 파티션, 오프셋, 예외 클래스처럼 최소한의 메타데이터 헤더만 추가한다. 예외 스택
트레이스와 예외 메시지는 DLQ 헤더에 추가하지 않는다.

DLQ 수동 확인:

```powershell
docker compose up -d kafka
.\gradlew.bat bootRun --args="--pingbell.check.dispatch-mode=kafka"
docker exec -it pingbell-kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic pingbell.health-check.requested.dlq --from-beginning --property print.headers=true
docker exec -it pingbell-kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic pingbell.health-check.completed.dlq --from-beginning --property print.headers=true
docker exec -it pingbell-kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic pingbell.notification.requested.dlq --from-beginning --property print.headers=true
```

DLQ 메시지는 페이로드만 보고 바로 재처리하지 않는다. `docs/dlq-reprocessing-policy.md`의 재처리 가능·폐기 기준에 따라 현재 DB 상태를 다시 확인한 뒤 판단한다. 현재 단계에서는 운영자용 단건 사전 점검, 단건 재처리, 목록 조회, 일괄 사전 점검 명령을 제공하며, 일괄 재처리와 DLQ 운영 UI/API는 제공하지 않는다.

수동 확인:

```powershell
docker compose up -d kafka
.\gradlew.bat bootRun --args="--pingbell.check.dispatch-mode=kafka"
```

실행 시점이 된 모니터를 생성하거나 기다린 후 다음 흐름을 확인한다.

```text
HealthCheckRequested
-> URL 체크 / CheckResult 저장
-> HealthCheckCompleted
-> 장애 판정
-> NotificationRequested
-> 알림 발송 / NotificationHistory 저장
```

### 3. 백엔드 실행

Windows PowerShell 환경:

```powershell
.\gradlew.bat bootRun
```

다른 포트로 실행해야 하는 경우:

```powershell
.\gradlew.bat bootRun --args="--server.port=18080"
```

헬스 체크:

```bash
curl http://localhost:8080/actuator/health
```

### 4. 프론트엔드 실행

```bash
cd frontend
npm install
npm run dev
```

브라우저 접속:

```text
http://localhost:5173
```

Vite 개발 서버는 `/api`, `/actuator` 요청을 백엔드 `http://localhost:8080`으로 프록시한다.

## Mailpit 사용법

로컬 기본 메일 발송은 실제 이메일 수신함이 아니라 Mailpit으로 들어간다.

1. `docker compose up -d`로 `pingbell-mailpit` 컨테이너를 실행한다.
2. 백엔드의 `MAIL_HOST=localhost`, `MAIL_PORT=1025` 설정을 사용한다.
3. 장애 발생 또는 복구 알림이 발송되면 브라우저에서 `http://localhost:8025`에 접속한다.
4. Mailpit 웹 화면에서 수신된 이메일 제목과 본문을 확인한다.

## Slack / Discord 웹훅 사용법

1. Slack 또는 Discord에서 수신 웹훅 URL을 생성한다.
2. Pingbell 프론트의 알림 채널 화면으로 이동한다.
3. 채널 타입을 `SLACK` 또는 `DISCORD`로 선택한다.
4. 대상에 웹훅 URL을 입력하고 저장한다.
5. 장애 발생 / 복구 이벤트를 발생시켜 해당 채널에 메시지가 도착하는지 확인한다.

OAuth 기반 연동은 아직 지원하지 않는다.

## 주요 API

### 인증

| 메서드 | 경로 | 설명 |
| --- | --- | --- |
| POST | `/api/auth/signup` | 회원가입 |
| POST | `/api/auth/login` | 로그인 |
| POST | `/api/auth/refresh` | 액세스 토큰 갱신 |
| POST | `/api/auth/logout` | 리프레시 토큰 폐기 |

### 모니터

| 메서드 | 경로 | 설명 |
| --- | --- | --- |
| POST | `/api/monitors` | 모니터 등록 |
| GET | `/api/monitors` | 모니터 목록 조회 |
| GET | `/api/monitors/{monitorId}` | 모니터 상세 조회 |
| PATCH | `/api/monitors/{monitorId}` | 모니터 설정 수정 |
| PATCH | `/api/monitors/{monitorId}/pause` | 모니터 일시정지 |
| PATCH | `/api/monitors/{monitorId}/activate` | 모니터 재활성화 |
| DELETE | `/api/monitors/{monitorId}` | 모니터 논리 삭제 |

### 체크 결과

| 메서드 | 경로 | 설명 |
| --- | --- | --- |
| GET | `/api/monitors/{monitorId}/checks` | 체크 결과 목록 조회 |
| GET | `/api/monitors/{monitorId}/checks/latest` | 최신 체크 결과 조회 |

### 장애

| 메서드 | 경로 | 설명 |
| --- | --- | --- |
| GET | `/api/incidents` | 장애 이력 목록 조회 |
| GET | `/api/incidents/{incidentId}` | 장애 상세 조회 |
| GET | `/api/monitors/{monitorId}/incidents` | 모니터별 장애 이력 조회 |

### 알림 채널

| 메서드 | 경로 | 설명 |
| --- | --- | --- |
| POST | `/api/notification-channels` | 이메일 / Slack / Discord 채널 등록 |
| GET | `/api/notification-channels` | 알림 채널 목록 조회 |
| PATCH | `/api/notification-channels/{publicId}` | 알림 채널 대상 수정 및 재활성화 |
| DELETE | `/api/notification-channels/{publicId}` | 알림 채널 비활성화 |

### 알림 이력

| 메서드 | 경로 | 설명 |
| --- | --- | --- |
| GET | `/api/notification-histories` | 내 계정의 알림 발송 이력 조회 |
| POST | `/api/notification-histories/{historyId}/resend` | 실패 알림 수동 재전송 |

## 검증 방법

### 백엔드

```powershell
.\gradlew.bat compileJava
.\gradlew.bat test
```

### 프론트엔드

```bash
cd frontend
npm install
npm run build
```

### Flyway 적용 상태 확인

```bash
docker exec pingbell-postgres psql -U pingbell -d pingbell -c "select version, script, success from flyway_schema_history order by installed_rank;"
```

## 수동 E2E 검증 절차

1. `docker compose up -d`
2. `.\gradlew.bat bootRun`
3. `cd frontend && npm run dev`
4. `http://localhost:5173` 접속
5. 회원가입
6. 로그인
7. 가입 이메일로 기본 이메일 알림 채널이 생성되었는지 확인
8. 필요하면 이메일 / Slack / Discord 알림 채널 추가
9. 실패하는 URL 또는 테스트 서버 URL로 모니터 등록
10. `failureThreshold` 이상 실패 발생 확인
11. `OPEN` 장애 생성 확인
12. 이메일은 Mailpit, Slack / Discord는 각 웹훅 채널에서 장애 발생 알림 확인
13. URL을 복구 가능한 상태로 변경
14. `recoveryThreshold` 이상 성공 발생 확인
15. 장애가 `RESOLVED`로 변경되었는지 확인
16. 이메일 / Slack / Discord에서 복구 알림 확인
17. 대시보드 / 목록 / 상세 화면에서 상태가 자연스럽게 보이는지 확인

## 최근 검증 결과

검증일: 2026-06-22

- `.\gradlew.bat test` 통과
- `cd frontend && npm run build` 통과

추가 검증일: 2026-06-27

- `.\gradlew.bat compileJava` 통과
- `.\gradlew.bat test --tests com.monit.pingbell.check.service.CheckServiceTest` 통과
- `.\gradlew.bat test --tests com.monit.pingbell.notification.service.*` 통과
- `cd frontend && npm run build` 통과

## 아직 제외된 범위

- Slack OAuth 연동
- Discord OAuth 연동
- 별도 작업자 애플리케이션 분리
- DLQ 수동 재처리 UI/API
- Prometheus / Grafana
- Kubernetes
- 마이크로서비스 아키텍처 분리

## 다음 작업 후보

구현 후보:

1. 비활성화된 알림 채널 숨김 또는 필터 추가
2. 모니터 수정 / 일시정지 / 삭제 사용자 경험 정리
3. 관측성 지표 중 단일 앱에서 먼저 볼 최소 지표 구현 검토

문서 / 운영 설계 후보:

1. 운영 설계 기반 구현 우선순위 재정리
2. Kafka 기반 작업자 분리 도입 시점 판단
3. Prometheus / Grafana 도입 범위 구체화
