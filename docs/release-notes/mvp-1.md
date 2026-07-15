# Pingbell MVP 1차 릴리즈 노트

릴리즈 기준일: 2026-06-19

> 이 문서는 MVP 1차 기준 릴리즈 노트로 보존한다.
> MVP 1차는 EMAIL 알림까지를 범위로 한다.
> Slack / Discord webhook 알림 채널은 MVP 2에서 추가되었으며, 자세한 내용은 `docs/release-notes/mvp-2.md`를 참고한다.

## 1. 릴리즈 요약

Pingbell MVP 1차는 개인 개발자가 본인의 서버 URL을 등록하고, 주기적인 헬스체크 결과를 기반으로 장애 발생과 복구를 판단한 뒤 EMAIL 알림을 받을 수 있는 단일 애플리케이션 버전이다.

이번 MVP는 고도화된 분산 구조보다 실제 동작하는 핵심 플로우 완성을 우선했다.

- 단일 Spring Boot 애플리케이션
- PostgreSQL + Flyway 기반 데이터 저장
- Docker Compose 기반 로컬 실행
- React 기반 기본 대시보드
- Mailpit 기반 로컬 이메일 검증

## 2. 포함된 기능

### 인증

- 회원가입
- 로그인
- JWT access token 발급
- 비밀번호 암호화 저장
- 회원가입 시 가입 이메일을 기본 EMAIL 알림 채널로 자동 생성

### 모니터링 URL 관리

- 모니터링 URL 등록
- 모니터링 URL 목록 조회
- 모니터링 URL 상세 조회
- URL scheme 검증
- interval, timeout, failure threshold, recovery threshold 설정

### 헬스체크

- Spring Scheduler 기반 주기적 URL 체크
- `nextCheckAt` 기준 체크 대상 조회
- ACTIVE / DOWN monitor 모두 체크 대상에 포함
- DOWN monitor도 계속 체크해 복구 여부 판단
- 체크 결과 저장
- 최신 체크 결과 및 체크 결과 목록 조회

체크 판정 기준:

- 1xx / 2xx / 3xx: 성공
- 4xx / 5xx: 실패
- timeout 예외: timeout
- status code가 없고 timeout이 아니면 실패

### 장애 판정

- 연속 실패 횟수가 `failureThreshold` 이상이면 incident OPEN
- 이미 OPEN incident가 있으면 중복 OPEN incident 생성 방지
- 장애 발생 시 monitor 상태를 DOWN으로 변경
- 연속 성공 횟수가 `recoveryThreshold` 이상이면 incident RESOLVED
- 장애 복구 시 monitor 상태를 ACTIVE로 변경
- 장애 이력 목록 / 상세 / monitor별 조회

### EMAIL 알림

- MVP 1차는 EMAIL 알림을 지원한다.
- 장애 발생 시 `INCIDENT_OPEN` 이메일 발송
- 장애 복구 시 `INCIDENT_RESOLVED` 이메일 발송
- 알림 발송 이력을 `NotificationHistory`에 저장
- 발송 성공 시 SENT 기록
- 발송 실패 시 FAILED와 error message 기록
- 알림 발송 실패가 체크 결과 저장이나 장애 판정을 롤백하지 않도록 분리
- 기존 계정에 알림 채널이 전혀 없으면 첫 알림 시 회원 이메일로 기본 EMAIL 채널 자동 생성

### 알림 채널 관리

- EMAIL 알림 채널 등록
- EMAIL 알림 채널 목록 조회
- EMAIL target 수정
- EMAIL 알림 채널 비활성화
- 비활성화된 EMAIL 채널 재활성화

삭제 정책:

- 채널 삭제는 hard delete가 아니다.
- `enabled=false` 비활성화로 처리한다.
- 알림 이력과의 관계를 보존하기 위한 정책이다.

### 프론트엔드

- 회원가입 화면
- 로그인 화면
- 기본 대시보드
- 모니터 목록 화면
- 모니터 등록 화면
- 모니터 상세 화면
- 체크 결과 조회 화면
- 장애 이력 조회 화면
- EMAIL 알림 채널 관리 화면

### 로컬 인프라

- Docker Compose 기반 PostgreSQL 16
- Docker Compose 기반 Mailpit
- Mailpit Web UI로 로컬 이메일 확인
- `.env.example` 기반 로컬 환경 변수 구성

## 3. 주요 API

### Auth

| Method | Path | 설명 |
| --- | --- | --- |
| POST | `/api/auth/signup` | 회원가입 |
| POST | `/api/auth/login` | 로그인 |

### Monitor

| Method | Path | 설명 |
| --- | --- | --- |
| POST | `/api/monitors` | 모니터 등록 |
| GET | `/api/monitors` | 모니터 목록 조회 |
| GET | `/api/monitors/{monitorId}` | 모니터 상세 조회 |

### Check Result

| Method | Path | 설명 |
| --- | --- | --- |
| GET | `/api/monitors/{monitorId}/checks` | 체크 결과 목록 조회 |
| GET | `/api/monitors/{monitorId}/checks/latest` | 최신 체크 결과 조회 |

### Incident

| Method | Path | 설명 |
| --- | --- | --- |
| GET | `/api/incidents` | 장애 이력 목록 조회 |
| GET | `/api/incidents/{incidentId}` | 장애 상세 조회 |
| GET | `/api/monitors/{monitorId}/incidents` | 모니터별 장애 이력 조회 |

### Notification Channel

| Method | Path | 설명 |
| --- | --- | --- |
| POST | `/api/notification-channels` | EMAIL 알림 채널 등록 |
| GET | `/api/notification-channels` | 알림 채널 목록 조회 |
| PATCH | `/api/notification-channels/{publicId}` | EMAIL target 수정 및 재활성화 |
| DELETE | `/api/notification-channels/{publicId}` | 알림 채널 비활성화 |

## 4. 검증 결과

검증일: 2026-06-19

수동 E2E 검증 완료:

- 회원가입
- 로그인
- 기본 EMAIL 알림 채널 생성
- 알림 채널 추가
- 알림 채널 수정
- 알림 채널 비활성화
- 알림 채널 재활성화
- 모니터 URL 등록
- 실패 threshold 도달
- incident OPEN 생성
- Mailpit 장애 발생 이메일 수신
- 복구 threshold 도달
- incident RESOLVED 변경
- Mailpit 장애 복구 이메일 수신
- 대시보드 / 목록 / 상세 화면 상태 확인

명령 기반 검증:

```powershell
.\gradlew.bat test
```

```bash
cd frontend
npm run build
```

## 5. 로컬 실행 요약

환경 변수 파일 생성:

```powershell
copy .env.example .env
```

인프라 실행:

```bash
docker compose up -d
```

백엔드 실행:

```powershell
.\gradlew.bat bootRun
```

프론트엔드 실행:

```bash
cd frontend
npm install
npm run dev
```

접속:

- Frontend: `http://localhost:5173`
- Backend health: `http://localhost:8080/actuator/health`
- Mailpit Web UI: `http://localhost:8025`

## 6. MVP 1차에서 제외된 범위

다음 기능은 MVP 1차에서는 제외했다. Slack / Discord webhook 알림은 이후 MVP 2에서 추가되었다.

- Slack 알림
- Discord 알림
- Slack OAuth
- Discord OAuth
- Kafka 기반 비동기 처리
- Check Worker 분리
- Incident Detector 분리
- Notification Worker 분리
- 알림 재시도
- DLQ
- Prometheus / Grafana
- Kubernetes
- MSA 분리

## 7. 다음 릴리즈

MVP 2에서는 다음 기능을 추가했다.

- Slack webhook 알림 채널
- Discord webhook 알림 채널
- EMAIL / SLACK / DISCORD 채널 관리
- 장애 발생 / 복구 시 채널별 알림 발송

자세한 내용은 `docs/release-notes/mvp-2.md`를 참고한다.
