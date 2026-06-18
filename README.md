# Pingbell

Pingbell은 개인 개발자용 서버 헬스체크 및 장애 알림 서비스입니다.
사용자가 모니터링할 URL을 등록하면 Spring Scheduler가 주기적으로 URL을 호출하고, 실패/timeout/응답 오류가 누적되면 장애로 판단해 이메일 알림을 보냅니다.

## MVP 1차 범위

- 회원가입 / 로그인
- JWT 인증
- 모니터링 URL 등록 / 조회
- Scheduler 기반 헬스체크
- 체크 결과 저장 / 조회
- 장애 발생 / 복구 판정
- 장애 이력 조회
- EMAIL 알림 채널 등록 / 조회
- 장애 발생 / 복구 이메일 알림
- React 기반 기본 대시보드
- Docker Compose 기반 로컬 PostgreSQL / SMTP 실행

## 기술 스택

Backend:

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

Frontend:

- Vite
- React
- TypeScript
- React Router
- TanStack Query
- React Hook Form

Local infra:

- Docker Compose
- PostgreSQL 16
- Mailpit

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

### 1. 환경 변수

루트에 `.env` 파일을 둡니다.

```bash
cp .env.example .env
```

기본 로컬 예시는 다음과 같습니다.

```env
POSTGRES_DB=pingbell
POSTGRES_USER=pingbell
POSTGRES_PASSWORD=pingbell1234!!
POSTGRES_PORT=5432

JWT_SECRET=change-this-to-a-long-random-secret

MAIL_HOST=localhost
MAIL_PORT=1025
MAIL_USERNAME=
MAIL_PASSWORD=
MAIL_SMTP_AUTH=false
MAIL_SMTP_STARTTLS_ENABLE=false

MAILPIT_SMTP_PORT=1025
MAILPIT_WEB_PORT=8025
```

실제 Gmail SMTP를 쓰려면 `MAIL_*` 값을 실제 설정으로 바꾸면 됩니다. 로컬 MVP 검증은 Mailpit 사용을 권장합니다.

### 2. 로컬 인프라 실행

```bash
docker compose up -d
```

확인:

```bash
docker ps --filter name=pingbell
```

접속 정보:

- PostgreSQL: `localhost:5432`
- Mailpit SMTP: `localhost:1025`
- Mailpit Web UI: `http://localhost:8025`

### 3. 백엔드 실행

```bash
./gradlew.bat bootRun
```

Windows PowerShell에서 포트를 바꿔 실행하려면:

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

브라우저에서 접속:

```text
http://localhost:5173
```

Vite dev server는 `/api`, `/actuator` 요청을 `http://localhost:8080`으로 프록시합니다.

## 주요 API

Auth:

| Method | Path | 설명 |
| --- | --- | --- |
| POST | `/api/auth/signup` | 회원가입 |
| POST | `/api/auth/login` | 로그인 |

Monitor:

| Method | Path | 설명 |
| --- | --- | --- |
| POST | `/api/monitors` | 모니터 등록 |
| GET | `/api/monitors` | 모니터 목록 |
| GET | `/api/monitors/{monitorId}` | 모니터 상세 |

Check Result:

| Method | Path | 설명 |
| --- | --- | --- |
| GET | `/api/monitors/{monitorId}/checks` | 체크 결과 목록 |
| GET | `/api/monitors/{monitorId}/checks/latest` | 최신 체크 결과 |

Incident:

| Method | Path | 설명 |
| --- | --- | --- |
| GET | `/api/incidents` | 장애 이력 목록 |
| GET | `/api/incidents/{incidentId}` | 장애 상세 |
| GET | `/api/monitors/{monitorId}/incidents` | 모니터별 장애 이력 |

Notification Channel:

| Method | Path | 설명 |
| --- | --- | --- |
| POST | `/api/notification-channels` | EMAIL 알림 채널 등록 |
| GET | `/api/notification-channels` | 알림 채널 목록 |

## 검증 방법

### 백엔드

```bash
./gradlew.bat compileJava
./gradlew.bat test
```

테스트는 PostgreSQL 컨테이너가 실행 중이어야 통과합니다.

### 프론트엔드

```bash
cd frontend
npm install
npm run build
```

### Flyway / DB FK 확인

마이그레이션 확인:

```bash
docker exec pingbell-postgres psql -U pingbell -d pingbell -c "select version, script, success from flyway_schema_history order by installed_rank;"
```

DB 외래키 제약 확인:

```bash
docker exec pingbell-postgres psql -U pingbell -d pingbell -c "select conname from pg_constraint where contype = 'f' and conrelid::regclass::text in ('monitors','check_results','incidents','notification_channels','notification_histories');"
```

현재 설계는 DB FK 제약을 두지 않고, JPA 연관관계와 서비스/인터셉터 검증으로 정합성을 관리합니다.

## 수동 E2E 확인 절차

1. `docker compose up -d`
2. `./gradlew.bat bootRun`
3. `cd frontend && npm run dev`
4. `http://localhost:5173` 접속
5. 회원가입
6. 로그인
7. EMAIL 알림 채널 등록
8. 모니터링 URL 등록
9. 스케줄러 실행 후 체크 결과 생성 확인
10. 장애 발생/복구 조건에 맞는 URL로 Incident 생성/복구 확인
11. `http://localhost:8025`에서 이메일 알림 확인

## 이번 검증 결과

검증일: 2026-06-18

- `docker compose up -d mailpit` 성공
- `pingbell-postgres` healthy 확인
- `pingbell-mailpit` healthy 확인
- Flyway V1~V7 적용 확인
- 대상 테이블 DB foreign key constraint 0개 확인
- `./gradlew.bat test` 성공
- `frontend npm run build` 성공
- `bootRun` 부팅 로그상 Flyway validate 및 Tomcat start 확인

런타임 HTTP E2E는 PowerShell 백그라운드 프로세스/포트 재사용 문제로 자동화 완료까지는 진행하지 못했습니다. 브라우저 기준 수동 확인은 위 절차대로 진행하면 됩니다.

## MVP 이후 범위

- Slack / Discord 알림
- 알림 채널 수정 / 삭제
- 모니터 수정 / 일시정지 / 삭제
- 대시보드 통계 API
- p95 / p99 응답 시간
- Kafka 기반 Worker 분리
- Prometheus / Grafana
- Kubernetes 배포
