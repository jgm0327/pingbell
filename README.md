# Pingbell

> 개인 개발자와 소규모 팀을 위한 서버 헬스체크 및 장애 알림 서비스

Pingbell은 사용자가 등록한 서버 URL을 주기적으로 호출하고, 응답 실패·타임아웃·5xx 응답·응답 지연 등을 감지해 장애 발생 및 복구 알림을 제공하는 서비스입니다.

단순히 URL이 살아 있는지만 확인하는 것이 아니라, 연속 실패 횟수와 복구 기준을 함께 관리하여 일시적인 네트워크 흔들림으로 인한 불필요한 알림을 줄이는 것을 목표로 합니다.

---

## 주요 기능

### 사용자 기능

- 회원가입 / 로그인
- JWT 기반 인증
- 모니터링 대상 URL 등록
- 체크 주기 설정
- 타임아웃 기준 설정
- 장애 판단 기준 설정
- 알림 채널 등록
- 체크 결과 조회
- 장애 이력 조회

### 모니터링 기능

- 등록된 URL 주기적 헬스체크
- HTTP 상태 코드 수집
- 응답 시간 측정
- 타임아웃 감지
- 연속 실패 횟수 기반 장애 판단
- 연속 성공 횟수 기반 장애 복구 판단

### 알림 기능

- 이메일 알림
- Slack Webhook 알림
- Discord Webhook 알림
- 알림 발송 이력 저장
- 중복 알림 방지
- 알림 실패 시 재시도 구조 확장

---

## 서비스 흐름

```text
사용자
  ↓
모니터링 URL 등록
  ↓
API Server
  ↓
DB 저장
  ↓
Scheduler
  ↓
주기적으로 URL 호출
  ↓
Check Result 저장
  ↓
장애 여부 판단
  ↓
Incident 생성 / 복구 처리
  ↓
Notification 발송
```

MVP 단계에서는 하나의 Spring Boot 애플리케이션 안에서 API, Scheduler, 장애 판단, 알림 발송을 함께 처리합니다.

이후 확장 단계에서는 Check Worker, Incident Detector, Notification Worker를 분리하고 Kafka 기반 이벤트 처리 구조로 발전시킬 예정입니다.

---

## 기술 스택

### Backend

- Java 21
- Spring Boot
- Spring Web
- Spring Security
- JWT
- Spring Data JPA
- QueryDSL
- WebClient
- Spring Scheduler
- Bean Validation

### Database

- PostgreSQL
- Flyway 또는 Liquibase
- JPA Auditing

### Infra

- Docker
- Docker Compose
- GitHub Actions
- Prometheus
- Grafana

### 확장 예정

- Apache Kafka
- Kubernetes
- Ingress Nginx
- Helm
- Loki 또는 ELK
- Argo CD

---

## 프로젝트 구조

```text
pingbell
├── src
│   ├── main
│   │   ├── java
│   │   │   └── com.monit.pingbell
│   │   │       ├── PingbellApplication.java
│   │   │       ├── global
│   │   │       │   ├── config
│   │   │       │   ├── exception
│   │   │       │   ├── security
│   │   │       │   └── common
│   │   │       ├── auth
│   │   │       ├── user
│   │   │       ├── monitor
│   │   │       ├── check
│   │   │       ├── incident
│   │   │       └── notification
│   │   └── resources
│   │       ├── application.yml
│   │       ├── application-local.yml
│   │       └── db
│   │           └── migration
│   └── test
├── docker-compose.yml
├── Dockerfile
├── build.gradle
└── README.md
```

---

## 도메인 설명

### User

서비스를 사용하는 회원입니다.

주요 역할:

- 모니터링 대상 관리
- 알림 채널 관리
- 장애 이력 확인

### Monitor

사용자가 등록한 헬스체크 대상입니다.

예시:

```text
https://api.example.com/health
```

주요 속성:

- URL
- HTTP Method
- 체크 주기
- 타임아웃 기준
- 실패 판단 기준
- 복구 판단 기준
- 활성화 상태

### Check Result

각 헬스체크 실행 결과입니다.

저장 정보:

- 성공 / 실패 여부
- HTTP 상태 코드
- 응답 시간
- 에러 메시지
- 체크 시각

### Incident

장애 발생 및 복구 이력입니다.

장애는 단일 체크 실패가 아니라, 설정된 연속 실패 기준을 넘었을 때 생성됩니다.

예시:

```text
3회 연속 실패 → 장애 발생
2회 연속 성공 → 장애 복구
```

### Notification Channel

사용자가 등록한 알림 채널입니다.

지원 예정 채널:

- Email
- Slack
- Discord

### Notification History

알림 발송 결과 이력입니다.

저장 정보:

- 발송 대상 장애
- 발송 채널
- 발송 상태
- 재시도 횟수
- 발송 시각

---

## ERD 개요

```text
users
  └── monitors
        ├── check_results
        └── incidents
              └── notification_histories

users
  └── notification_channels
        └── notification_histories
```

---

## 테이블 설계

### users

| 컬럼 | 설명 |
| --- | --- |
| id | 사용자 ID |
| email | 로그인 이메일 |
| password | 암호화된 비밀번호 |
| created_at | 생성 시각 |
| updated_at | 수정 시각 |

### monitors

| 컬럼 | 설명 |
| --- | --- |
| id | 모니터링 대상 ID |
| user_id | 사용자 ID |
| name | 모니터링 이름 |
| url | 체크 대상 URL |
| method | HTTP Method |
| interval_seconds | 체크 주기 |
| timeout_millis | 타임아웃 기준 |
| failure_threshold | 장애 판단 연속 실패 횟수 |
| recovery_threshold | 복구 판단 연속 성공 횟수 |
| status | ACTIVE / PAUSED / DELETED |
| next_check_at | 다음 체크 예정 시각 |
| created_at | 생성 시각 |
| updated_at | 수정 시각 |

### check_results

| 컬럼 | 설명 |
| --- | --- |
| id | 체크 결과 ID |
| monitor_id | 모니터링 대상 ID |
| status | SUCCESS / FAILURE / TIMEOUT |
| http_status | HTTP 상태 코드 |
| response_time_ms | 응답 시간 |
| error_message | 에러 메시지 |
| checked_at | 체크 시각 |

### incidents

| 컬럼 | 설명 |
| --- | --- |
| id | 장애 ID |
| monitor_id | 모니터링 대상 ID |
| status | OPEN / RESOLVED |
| started_at | 장애 발생 시각 |
| resolved_at | 장애 복구 시각 |
| failure_count | 장애 발생 당시 실패 횟수 |
| last_error_message | 마지막 에러 메시지 |

### notification_channels

| 컬럼 | 설명 |
| --- | --- |
| id | 알림 채널 ID |
| user_id | 사용자 ID |
| type | EMAIL / SLACK / DISCORD |
| target | 이메일 주소 또는 Webhook URL |
| enabled | 사용 여부 |
| created_at | 생성 시각 |
| updated_at | 수정 시각 |

### notification_histories

| 컬럼 | 설명 |
| --- | --- |
| id | 알림 이력 ID |
| incident_id | 장애 ID |
| channel_id | 알림 채널 ID |
| status | PENDING / SENT / FAILED |
| retry_count | 재시도 횟수 |
| sent_at | 발송 시각 |
| created_at | 생성 시각 |

---

## API 설계

### Auth

| Method | URI | 설명 |
| --- | --- | --- |
| POST | `/api/auth/signup` | 회원가입 |
| POST | `/api/auth/login` | 로그인 |
| POST | `/api/auth/reissue` | 토큰 재발급 |

### Monitor

| Method | URI | 설명 |
| --- | --- | --- |
| POST | `/api/monitors` | 모니터링 대상 등록 |
| GET | `/api/monitors` | 모니터링 대상 목록 조회 |
| GET | `/api/monitors/{monitorId}` | 모니터링 대상 상세 조회 |
| PATCH | `/api/monitors/{monitorId}` | 모니터링 대상 수정 |
| PATCH | `/api/monitors/{monitorId}/pause` | 모니터링 일시 중지 |
| PATCH | `/api/monitors/{monitorId}/resume` | 모니터링 재개 |
| DELETE | `/api/monitors/{monitorId}` | 모니터링 대상 삭제 |

### Check Result

| Method | URI | 설명 |
| --- | --- | --- |
| GET | `/api/monitors/{monitorId}/checks` | 체크 결과 목록 조회 |
| GET | `/api/monitors/{monitorId}/checks/latest` | 최근 체크 결과 조회 |

### Incident

| Method | URI | 설명 |
| --- | --- | --- |
| GET | `/api/incidents` | 장애 이력 목록 조회 |
| GET | `/api/incidents/{incidentId}` | 장애 상세 조회 |
| GET | `/api/monitors/{monitorId}/incidents` | 특정 모니터링 대상의 장애 이력 조회 |

### Notification Channel

| Method | URI | 설명 |
| --- | --- | --- |
| POST | `/api/notification-channels` | 알림 채널 등록 |
| GET | `/api/notification-channels` | 알림 채널 목록 조회 |
| PATCH | `/api/notification-channels/{channelId}` | 알림 채널 수정 |
| DELETE | `/api/notification-channels/{channelId}` | 알림 채널 삭제 |
| POST | `/api/notification-channels/{channelId}/test` | 테스트 알림 발송 |

---

## 장애 판단 기준

Pingbell은 단일 실패만으로 장애를 생성하지 않습니다.

예를 들어 모니터링 대상의 기준이 다음과 같다면,

```text
failure_threshold = 3
recovery_threshold = 2
```

아래와 같이 동작합니다.

```text
실패 1회 → 체크 실패만 저장
실패 2회 → 체크 실패만 저장
실패 3회 → 장애 OPEN 생성

성공 1회 → 아직 장애 유지
성공 2회 → 장애 RESOLVED 처리
```

이를 통해 순간적인 네트워크 지연이나 일시적인 외부 서버 문제로 인해 불필요한 알림이 반복되는 것을 줄입니다.

---

## 중복 알림 방지

이미 열린 장애가 있다면 같은 모니터링 대상에 대해 새로운 장애를 만들지 않습니다.

```text
monitor_id + status = OPEN
```

또한 같은 장애에 대해 같은 채널로 동일한 알림이 중복 발송되지 않도록 알림 이력 기준의 중복 방지 구조를 둡니다.

예시:

```text
incident_id + channel_id + notification_type
```

---

## 실행 방법

### 1. Repository clone

```bash
git clone https://github.com/{username}/pingbell.git
cd pingbell
```

### 2. Docker Compose 실행

```bash
docker compose up -d
```

### 3. 애플리케이션 실행

```bash
./gradlew bootRun
```

### 4. 테스트 실행

```bash
./gradlew test
```

---

## 환경 변수

```env
SPRING_PROFILES_ACTIVE=local

DB_HOST=localhost
DB_PORT=5432
DB_NAME=pingbell
DB_USERNAME=pingbell
DB_PASSWORD=pingbell

JWT_SECRET=change-me-change-me-change-me-change-me
JWT_ACCESS_TOKEN_EXPIRE_SECONDS=3600
JWT_REFRESH_TOKEN_EXPIRE_SECONDS=1209600

MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=example@gmail.com
MAIL_PASSWORD=app-password
```

---

## Docker Compose 예시

```yaml
services:
  postgres:
    image: postgres:16
    container_name: pingbell-postgres
    environment:
      POSTGRES_DB: pingbell
      POSTGRES_USER: pingbell
      POSTGRES_PASSWORD: pingbell
    ports:
      - "5432:5432"
    volumes:
      - pingbell-postgres-data:/var/lib/postgresql/data

volumes:
  pingbell-postgres-data:
```

---

## MVP 개발 범위

### MVP 1차

- 회원가입 / 로그인
- JWT 인증
- 모니터링 URL 등록
- 1분 단위 헬스체크
- 체크 결과 저장
- 장애 발생 / 복구 판정
- 이메일 알림
- Docker Compose 기반 로컬 실행

### MVP 2차

- Slack / Discord 알림
- 알림 실패 재시도
- 알림 이력 관리
- 체크 결과 검색 조건 추가
- 장애 통계 조회

### MVP 3차

- Kafka 도입
- Check Worker 분리
- Notification Worker 분리
- DLQ 구성
- Prometheus / Grafana 모니터링
- Kubernetes 배포

---

## 확장 아키텍처

MVP 이후에는 아래와 같이 이벤트 기반 구조로 확장합니다.

```text
API Server
  ↓
DB 저장

Scheduler
  ↓
monitor-check-request 발행

Check Worker
  ↓
URL 호출
  ↓
Check Result 저장
  ↓
monitor-check-result 발행

Incident Detector
  ↓
장애 발생 / 복구 판단
  ↓
incident-notification 발행

Notification Worker
  ↓
Email / Slack / Discord 알림 발송
```

Kafka Topic 예시:

```text
monitor-check-request
monitor-check-result
incident-notification
incident-notification-dlq
```

---

## 모니터링 지표

운영 단계에서는 다음 지표를 수집합니다.

```text
헬스체크 성공률
평균 응답 시간
p95 / p99 응답 시간
장애 발생 수
장애 복구 시간
알림 발송 성공률
알림 재시도 횟수
Kafka Consumer Lag
API 서버 CPU / Memory
Pod Restart Count
```

특히 평균 응답 시간만으로는 일부 지연 요청을 놓칠 수 있으므로 p95, p99 지표를 함께 확인합니다.

---

## 포트폴리오에서 강조할 수 있는 점

이 프로젝트는 단순 CRUD 서비스가 아니라, 실제 운영 상황에서 필요한 안정성 요소를 경험하기 위한 프로젝트입니다.

강조 포인트:

- 주기적 작업 처리
- 외부 서버 호출 실패 대응
- 타임아웃 관리
- 장애 발생 / 복구 기준 설계
- 중복 알림 방지
- 비동기 이벤트 처리 구조 확장
- 알림 실패 재시도 및 DLQ
- p95 / p99 기반 관측
- Docker Compose 기반 실행 환경 구성
- Kubernetes 기반 운영 환경 확장

---

## 향후 개선 방향

- 모니터링 대상별 커스텀 헤더 설정
- HTTP Method별 요청 Body 설정
- 특정 문자열 포함 여부 검사
- SSL 인증서 만료일 체크
- 도메인 만료일 체크
- 알림 정책 세분화
- 사용자별 대시보드 제공
- 장애 리포트 자동 생성
- Web Push 알림 지원
- 멀티 테넌트 구조 개선

---

## License

This project is licensed under the MIT License.
