# DLQ Payload And Security

## DLQ Payload 기준

DLQ payload에는 재처리와 원인 분석에 필요한 최소 정보만 넣는다.

포함 후보:

```text
dlqId
originalEventId
originalEventType
failedAt
failureStage
failureType
errorClass
errorMessage
retryCount
maxRetryCount
monitorId
checkResultId
incidentId
notificationHistoryId
channelId
channelType
notificationType
payloadVersion
manualReprocessable
```

제외할 값:

- monitor URL 원문
- email address 원문
- Slack webhook URL
- Discord webhook URL
- 복호화된 notification target
- JWT, API key, SMTP password 같은 secret
- Authorization header
- 사용자 비밀번호 또는 인증 토큰

보안 기준:

- DLQ는 운영자가 조회할 수 있으므로 secret 저장소가 아니다.
- 원인 분석은 id 기반으로 DB를 다시 조회하는 방식으로 한다.
- 실패 메시지에 URL이나 token이 포함될 수 있으면 redaction 후 저장한다.

## 운영자가 확인해야 할 상태

운영자가 확인할 최소 상태:

- DLQ event count by event type
- DLQ event count by failure stage
- retry pending count
- retry exhausted count
- oldest DLQ event age
- notification failed count by channel type
- check worker processing failure count
- incident detector processing failure count
- notification worker processing failure count

운영 로그에 남길 값:

- `eventId`
- `eventType`
- `failureStage`
- `failureType`
- `monitorId`
- `checkResultId`
- `incidentId`
- `notificationHistoryId`
- `channelId`
- redacted error message

로그에 남기지 않을 값:

- monitor URL 원문
- webhook URL
- email address 원문
- 복호화된 target
- secret

## Payload와 DB 기준 복구 가능성

현재 Kafka payload와 DLQ payload에는 secret을 넣지 않는다.

| 항목 | payload 포함 여부 | 재처리 시 복구 기준 |
| --- | --- | --- |
| monitor URL | 포함하지 않음 | `monitorId`로 `Monitor`를 다시 조회한다. |
| notification target | 포함하지 않음 | `memberId`와 incident 기준으로 활성 `NotificationChannel`을 다시 조회한다. |
| webhook URL | 포함하지 않음 | 암호화된 DB target을 복호화해 사용한다. |
| email address | 포함하지 않음 | EMAIL channel target 또는 member email 기준으로 재구성한다. |
| secret / token | 포함하지 않음 | 환경변수와 DB 암호화 값을 사용한다. |

결론:

- 정상적인 id 관계가 유지되어 있으면 DB source of truth 기준으로 복구 가능하다.
- payload에 id가 없거나 DB 관계가 깨진 경우에는 자동/수동 재처리보다 데이터 보정 또는 폐기가 우선이다.

