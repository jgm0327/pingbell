# Pingbell MVP 2 릴리즈 노트

릴리즈 기준일: 2026-06-19

## 1. 릴리즈 요약

Pingbell MVP 2는 MVP 1의 EMAIL 기반 장애 알림을 확장해 Slack과 Discord webhook 알림 채널을 지원하는 버전이다.

MVP 1은 서버 헬스체크, 장애 발생 / 복구 판정, EMAIL 알림까지 동작하는 단일 애플리케이션 버전이었다. MVP 2는 같은 단일 Spring Boot 구조를 유지하면서 알림 채널을 `EMAIL / SLACK / DISCORD`로 확장했다.

이번 릴리즈에서도 Kafka, 별도 worker, OAuth, 재시도 큐 같은 고도화 구조는 도입하지 않았다.

## 2. 포함된 기능

### Slack 알림

- Slack webhook URL을 알림 채널 target으로 등록할 수 있다.
- 장애 발생 시 Slack으로 `INCIDENT_OPEN` 메시지를 발송한다.
- 장애 복구 시 Slack으로 `INCIDENT_RESOLVED` 메시지를 발송한다.
- Slack 발송 성공 / 실패는 `NotificationHistory`에 기록된다.

### Discord 알림

- Discord webhook URL을 알림 채널 target으로 등록할 수 있다.
- 장애 발생 시 Discord로 `INCIDENT_OPEN` 메시지를 발송한다.
- 장애 복구 시 Discord로 `INCIDENT_RESOLVED` 메시지를 발송한다.
- Discord 발송 성공 / 실패는 `NotificationHistory`에 기록된다.

### 알림 채널 관리

지원 채널 타입:

- `EMAIL`
- `SLACK`
- `DISCORD`

지원 기능:

- 채널 등록
- 채널 목록 조회
- target 수정
- 채널 비활성화
- 비활성화된 채널 재활성화

비활성화 정책:

- 채널 삭제 요청은 DB row hard delete가 아니다.
- `enabled=false`로 비활성화한다.
- 기존 알림 이력과 채널의 관계를 보존하기 위한 정책이다.

target 노출 정책:

- webhook URL은 secret으로 취급한다.
- `notification_channels.target`은 DB 저장 시 암호화한다.
- 암호화 키는 `NOTIFICATION_TARGET_ENCRYPTION_KEY` 환경변수로 관리한다.
- 기존 평문 target은 앱 시작 시 backfill로 암호화한다.
- 기존 평문 target은 읽기 호환도 유지한다.
- 알림 채널 응답에는 원본 target을 포함하지 않는다.
- API 응답과 프론트 목록에는 `maskedTarget`만 노출한다.
- target 수정 / 재활성화 시에는 새 target을 다시 입력한다.

### 장애 발생 / 복구 알림

- 장애 발생 시 활성화된 모든 알림 채널로 알림을 보낸다.
- 장애 복구 시 활성화된 모든 알림 채널로 알림을 보낸다.
- 같은 incident, 같은 channel, 같은 notification type에 대해 중복 발송하지 않는다.
- 발송 실패는 해당 채널의 `NotificationHistory`에 FAILED로 기록된다.
- 발송 실패가 체크 결과 저장이나 장애 판정을 롤백하지 않는다.

### 프론트엔드

알림 채널 화면에서 다음 기능을 지원한다.

- EMAIL / SLACK / DISCORD 타입 선택
- target 입력
- 채널 목록 조회
- target 수정
- 비활성화
- 재활성화

## 3. 주요 API

### Notification Channel

| Method | Path | 설명 |
| --- | --- | --- |
| POST | `/api/notification-channels` | EMAIL / SLACK / DISCORD 채널 등록 |
| GET | `/api/notification-channels` | 알림 채널 목록 조회 |
| PATCH | `/api/notification-channels/{publicId}` | target 수정 및 재활성화 |
| DELETE | `/api/notification-channels/{publicId}` | 채널 비활성화 |

요청 예시:

```json
{
  "type": "SLACK",
  "target": "https://hooks.slack.com/services/..."
}
```

```json
{
  "type": "DISCORD",
  "target": "https://discord.com/api/webhooks/..."
}
```

## 4. 검증 결과

검증일: 2026-06-19

명령 기반 검증:

```powershell
.\gradlew.bat test
```

```powershell
cd frontend
npm run build
```

검증된 내용:

- EMAIL 기존 테스트 유지
- Slack sender payload 검증
- Discord sender payload 검증
- Slack channel 등록 / 수정 테스트
- Discord channel 등록 / 수정 테스트
- 알림 채널 응답의 target 마스킹 검증
- 알림 채널 target 암호화 / 복호화 검증
- 프론트 TypeScript 빌드 통과

## 5. 수동 E2E 검증 절차

1. `docker compose up -d`
2. `.\gradlew.bat bootRun`
3. `cd frontend && npm run dev`
4. `http://localhost:5173` 접속
5. 회원가입 / 로그인
6. 알림 채널 화면에서 Slack webhook 채널 등록
7. 알림 채널 화면에서 Discord webhook 채널 등록
8. 실패하는 monitor URL 등록
9. `failureThreshold` 이상 실패를 발생시킨다.
10. incident OPEN 생성 확인
11. Slack / Discord에 장애 발생 알림 도착 확인
12. monitor URL을 정상 응답으로 복구한다.
13. `recoveryThreshold` 이상 성공을 발생시킨다.
14. incident RESOLVED 변경 확인
15. Slack / Discord에 장애 복구 알림 도착 확인

## 6. 수동 E2E 검증 결과

검증일: 2026-06-19

검증 상태:

- Slack webhook 알림: 사용자 수동 검증 완료
- Discord webhook 알림: 사용자 수동 검증 완료

확인된 내용:

- Slack webhook URL을 알림 채널로 등록할 수 있다.
- Discord webhook URL을 알림 채널로 등록할 수 있다.
- 장애 발생 시 Slack 채널로 알림이 도착한다.
- 장애 복구 시 Slack 채널로 알림이 도착한다.
- 장애 발생 시 Discord 채널로 알림이 도착한다.
- 장애 복구 시 Discord 채널로 알림이 도착한다.
- 프론트 알림 채널 화면에서 EMAIL / SLACK / DISCORD 타입 선택이 동작한다.

비고:

- Slack / Discord OAuth는 검증 대상이 아니다.
- 알림 재시도는 아직 구현되지 않았으므로 검증 대상이 아니다.

## 7. 제외 범위

MVP 2에서 제외한 기능:

- Slack OAuth
- Discord OAuth
- Kafka / Worker 분리
- 알림 재시도
- DLQ
- Prometheus / Grafana
- Kubernetes
- MSA 분리

## 8. 알려진 제한사항

- Slack / Discord는 webhook URL 기반으로만 연동한다.
- Slack / Discord webhook URL은 `http` 또는 `https` URL 형식만 검증한다.
- `NOTIFICATION_TARGET_ENCRYPTION_KEY`를 잃어버리면 암호화된 target을 복호화할 수 없다.
- 발송 실패 시 즉시 재시도하지 않는다.
- 알림 발송은 현재 단일 Spring Boot 애플리케이션 내부에서 수행한다.

## 9. 다음 작업 후보

1. disabled 알림 채널 숨김 또는 필터 추가
2. 알림 재시도 정책 설계
3. NotificationHistory 조회 화면 또는 API 추가
4. Kafka 기반 notification worker 분리 설계
