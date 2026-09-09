# Pingbell 장애 알림 기준 정책

작성일: 2026-06-23

## 1. 문서 목적

이 문서는 Pingbell에서 어떤 에러를 사용자에게 알림으로 보낼지 정리한 정책 문서다.

Pingbell은 서버 헬스체크 결과를 기반으로 장애를 감지하고 알림을 보내는 서비스다. 하지만 모든 실패를 즉시 알림으로 보내면 다음 문제가 생긴다.

- 일시적인 네트워크 흔들림에도 알림이 과도하게 발생한다.
- 사용자가 알림을 무시하게 되어 실제 치명적인 장애를 놓칠 수 있다.
- 이메일, Slack, Discord 발송 트래픽과 비용이 불필요하게 증가한다.
- Pingbell 자체의 알림 시스템 부하가 커진다.

따라서 Pingbell은 "체크 실패"와 "알림이 필요한 장애"를 구분한다.

## 2. 현재 판단

MVP 초기 단계에서는 너무 복잡한 알림 정책을 바로 구현하지 않는다.

현재 권장 방향:

- MVP 1~2: 기본 장애 발생 / 복구 알림만 유지한다.
- MVP 3: 알림 억제, 심각도, 반복 알림 방지 정책을 도입한다.
- MVP 이후: 사용자별 커스텀 알림 정책, 알림 그룹화, 유지보수 시간대 설정을 도입한다.

즉, 지금은 구현보다 정책을 먼저 정리해두고, MVP 3에서 이슈 단위로 나누어 적용한다.

## 3. 용어 정의

| 용어 | 설명 |
| --- | --- |
| Check Failure | 단일 헬스체크 요청이 실패한 상태 |
| Incident | 연속 실패 기준을 넘어서 실제 장애로 판단된 상태 |
| Incident Open | 장애가 새로 발생한 상태 |
| Incident Resolved | 장애가 복구된 상태 |
| Notification | 사용자에게 이메일, Slack, Discord 등으로 보내는 알림 |
| Suppression | 조건에 따라 알림 발송을 억제하는 정책 |
| Severity | 장애의 심각도 |

## 4. 기본 원칙

Pingbell의 알림 기준은 다음 원칙을 따른다.

- 단일 실패만으로는 알림을 보내지 않는다.
- 연속 실패가 임계값을 넘을 때만 장애로 판단한다.
- 같은 장애에 대해 반복 알림을 과도하게 보내지 않는다.
- 장애 발생 알림과 복구 알림은 구분한다.
- 사용자가 조치해야 하는 상황을 우선 알린다.
- Pingbell 내부 알림 발송 실패는 모니터링 대상 서버의 장애로 보지 않는다.

## 5. 체크 실패로 기록할 대상

다음 상황은 `CheckResult`에 실패로 기록한다.

- DNS 조회 실패
- TCP 연결 실패
- connection timeout
- read timeout
- HTTP 4xx 응답
- HTTP 5xx 응답
- 설정한 응답 시간 기준 초과
- SSL 인증서 오류
- redirect 횟수 초과
- 응답 본문 검증 실패

단, 실패로 기록한다고 해서 항상 사용자 알림을 보내지는 않는다.

## 6. 알림을 보내는 기본 기준

알림은 `Incident` 상태 전환이 발생할 때 보낸다.

기본 정책:

- `failureThreshold`회 연속 실패하면 `Incident Open` 알림을 보낸다.
- 이미 열린 incident가 있으면 같은 장애에 대해 새 알림을 보내지 않는다.
- `recoveryThreshold`회 연속 성공하면 `Incident Resolved` 알림을 보낸다.
- 복구 알림은 실제로 열린 incident가 있었을 때만 보낸다.

예시:

```text
failureThreshold = 3
recoveryThreshold = 2

SUCCESS -> FAIL -> FAIL -> FAIL
=> Incident Open 알림 발송

FAIL -> SUCCESS -> SUCCESS
=> Incident Resolved 알림 발송
```

## 7. 알림을 바로 보내지 않는 경우

다음은 체크 실패로 저장하되 즉시 알림을 보내지 않는 것을 권장한다.

- 첫 번째 실패
- `failureThreshold`에 도달하지 않은 연속 실패
- 이미 같은 monitor에 열린 incident가 있는 상태의 추가 실패
- Pingbell의 이메일 / Slack / Discord 발송 실패
- 사용자가 비활성화한 monitor의 실패
- soft delete된 monitor의 실패

## 8. HTTP 상태 코드 기준

HTTP 응답 기준은 다음과 같이 분류한다.

| 응답 | CheckResult | Incident 판단 | 비고 |
| --- | --- | --- | --- |
| 1xx | SUCCESS | 성공으로 처리 | MVP에서는 성공 취급 |
| 2xx | SUCCESS | 성공으로 처리 | 정상 |
| 3xx | SUCCESS | 성공으로 처리 | redirect 허용 범위 내 |
| 400 | HTTP_ERROR | 연속 실패 기준 적용 | API health endpoint 구성 오류 가능 |
| 401 / 403 | HTTP_ERROR | 연속 실패 기준 적용 | 인증 설정 문제 가능 |
| 404 | HTTP_ERROR | 연속 실패 기준 적용 | endpoint 삭제 가능 |
| 408 | TIMEOUT | 연속 실패 기준 적용 | timeout 계열 |
| 429 | HTTP_ERROR | 연속 실패 기준 적용 | rate limit 가능 |
| 5xx | HTTP_ERROR | 연속 실패 기준 적용 | 서버 장애 가능성이 높음 |

MVP에서는 4xx와 5xx 모두 실패로 본다. 다만 이후 고도화 단계에서는 사용자가 4xx를 실패로 볼지 선택할 수 있게 할 수 있다.

## 9. 응답 지연 기준

응답은 성공했지만 너무 느린 경우도 장애 후보로 본다.

권장 정책:

- monitor별 `timeoutMillis`는 요청 자체의 최대 대기 시간이다.
- 현재 MVP에서는 응답 시간이 monitor의 `timeoutMillis`를 넘으면 `SLOW_RESPONSE`로 기록한다.
- 이후 고도화에서는 monitor별 `slowResponseThresholdMillis`를 별도 필드로 둘 수 있다.
- `SLOW_RESPONSE`가 연속으로 발생하면 warning 또는 incident로 판단할 수 있다.

MVP 3 권장:

- 처음에는 `SLOW_RESPONSE`를 별도 알림으로 보내지 않는다.
- 대시보드와 체크 결과에만 표시한다.
- 이후 사용자가 원할 때 slow response 알림을 켤 수 있게 한다.

## 10. 심각도 기준

MVP 3에서 도입할 수 있는 기본 심각도는 다음과 같다.

| Severity | 조건 | 알림 |
| --- | --- | --- |
| INFO | 복구됨, 설정 변경성 이벤트 | 기본적으로 낮은 우선순위 |
| WARNING | 응답 지연, 짧은 연속 실패 | 선택 알림 |
| CRITICAL | 연속 실패로 incident open | 즉시 알림 |

초기 구현에서는 `CRITICAL`만 사용자에게 적극적으로 알린다.

## 11. 알림 과다 발생 방지 기준

MVP 3에서 다음 정책을 적용하는 것을 권장한다.

### 11.1 같은 incident 중복 알림 방지

같은 monitor에 열린 incident가 있으면 추가 실패가 계속 발생해도 새 `INCIDENT_OPEN` 알림을 보내지 않는다.

```text
FAIL -> FAIL -> FAIL
=> Incident Open 알림 1회

FAIL -> FAIL -> FAIL -> FAIL
=> 추가 open 알림 없음
```

### 11.2 반복 알림 쿨다운

장애가 장시간 지속될 경우 반복 알림을 보낼 수 있지만, 반드시 쿨다운을 둔다.

권장 기본값:

- 기본 반복 알림 없음
- 추후 옵션으로 30분, 1시간, 3시간 단위 반복 알림 제공
- 반복 알림은 `still down` 성격으로 별도 notification type을 둔다.

### 11.3 flapping 방지

짧은 시간 안에 장애와 복구가 반복되는 상태를 flapping으로 본다.

예시:

```text
OPEN -> RESOLVED -> OPEN -> RESOLVED
```

권장 정책:

- 일정 시간 안에 open / resolved가 여러 번 반복되면 복구 알림을 지연한다.
- MVP 3에서는 구현하지 않고 정책만 남긴다.
- MVP 이후 `flappingWindowMinutes`, `flappingCountThreshold` 설정을 고려한다.

### 11.4 알림 채널별 rate limit

채널별로 발송 제한을 둘 수 있다.

권장 기본값:

- monitor 1개당 open 알림은 incident 1개에 1회
- monitor 1개당 resolved 알림은 incident 1개에 1회
- 같은 사용자에게 분당 최대 알림 수 제한은 MVP 이후 도입

## 12. 알림 우선순위

여러 이벤트가 동시에 발생하면 다음 우선순위를 따른다.

1. Incident Open
2. Incident Resolved
3. Notification 발송 실패
4. Slow response
5. 단일 Check Failure

MVP에서는 1, 2만 사용자 알림 대상으로 본다.

## 13. Pingbell 내부 에러와 사용자 서버 에러 분리

Pingbell 내부 에러는 사용자 서버의 장애와 분리해야 한다.

예시:

- 이메일 SMTP 발송 실패
- Slack webhook 발송 실패
- Discord webhook 발송 실패
- notification target 복호화 실패
- Pingbell DB 저장 실패

이런 에러는 `NotificationHistory` 또는 내부 로그에 기록하되, monitor의 `failureCount`나 incident 판단에 반영하지 않는다.

이유:

- 사용자 서버는 정상인데 Pingbell 알림 발송만 실패했을 수 있다.
- 알림 발송 실패를 다시 사용자 서버 장애로 판단하면 잘못된 incident가 생긴다.
- 운영상 원인을 추적하기 어렵다.

## 14. MVP 3 구현 이슈 분해

### Issue 1. Check Failure와 Incident 알림 기준 문서화 반영

담당 에이전트: Backend

작업 범위:

- 현재 health check 실패 분류 확인
- `failureThreshold`, `recoveryThreshold` 기준 유지
- 단일 실패에서는 알림이 발송되지 않는지 테스트 보강
- 열린 incident가 있을 때 중복 open 알림이 나가지 않는지 테스트 보강

완료 조건:

- 연속 실패 기준에 도달해야 `INCIDENT_OPEN` 알림이 발송된다.
- 열린 incident가 있으면 추가 실패에도 open 알림이 중복 발송되지 않는다.

### Issue 2. Slow Response 기록 정책 추가

담당 에이전트: Backend

작업 범위:

- 응답 지연 기준 필드 필요 여부 검토
- `CheckResult`에 slow 상태를 남길지 결정
- MVP 3에서는 알림 발송 대상에서 제외

완료 조건:

- 느린 응답과 실패를 구분할 수 있다.
- slow response는 기본 알림으로 발송되지 않는다.

### Issue 3. Notification 중복 발송 방지 테스트 보강

담당 에이전트: Backend

작업 범위:

- 같은 incident에 대해 open 알림이 1회만 발송되는지 테스트
- resolved 알림이 open incident가 있을 때만 발송되는지 테스트
- notification 발송 실패가 incident 판단에 영향을 주지 않는지 테스트

완료 조건:

- 알림 발송 실패와 health check 실패가 분리된다.
- 같은 장애에 대해 불필요한 중복 알림이 발생하지 않는다.

### Issue 4. Frontend 알림 기준 표시

담당 에이전트: Frontend

작업 범위:

- monitor 상세 화면에 failure threshold / recovery threshold 표시
- 현재 연속 실패 횟수 표시 여부 검토
- incident 상태와 마지막 알림 발송 시각 표시

완료 조건:

- 사용자가 왜 알림이 발송되었는지 이해할 수 있다.
- 단일 실패가 있어도 아직 장애 알림이 아닌 상태를 구분할 수 있다.

## 15. 추후 고도화 후보

MVP 이후 다음 기능을 고려한다.

- 사용자별 알림 민감도 설정
- monitor별 4xx 실패 처리 옵션
- slow response 알림 on/off
- 반복 알림 주기 설정
- 유지보수 시간대 설정
- flapping 감지
- 알림 그룹화
- 사용자별 일일 / 시간당 알림 제한
- 장애 심각도별 채널 분리
- Slack / Discord mention 정책

## 16. 다음 작업

현재 단계에서는 이 정책을 구현하지 않는다.

다음 구현 순서는 다음을 권장한다.

1. MVP 1~2 기능 안정화
2. 알림 발송 실패 재시도 정책 구현
3. 수동 알림 재발송 정책 구현
4. MVP 3에서 이 문서를 기준으로 장애 알림 기준 고도화
