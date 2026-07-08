# DLQ Overview

## 목적

Kafka mode에서 DLQ에 쌓인 메시지를 운영자가 어떻게 확인하고, 어떤 기준으로 재처리하거나 폐기할지 정리한다.

현재 Pingbell은 별도 Worker 애플리케이션을 분리하지 않고, 단일 Spring Boot 앱 안에서 Kafka listener와 DLQ topic을 사용한다. 로컬 Docker Compose 환경에서 확인 가능한 최소 운영 절차와 재처리 판단 기준을 고정한다.

## 현재 원칙

현재 Pingbell은 단일 Spring Boot 애플리케이션 구조를 유지하되, `PINGBELL_CHECK_DISPATCH_MODE=kafka`일 때 Kafka event flow를 사용한다.

- direct mode 동작은 변경하지 않는다.
- 별도 Worker 애플리케이션을 만들지 않는다.
- DLQ table 또는 운영 UI는 만들지 않는다.
- DLQ topic은 source topic 이름 뒤에 `.dlq`를 붙인다.
- DLQ record는 원본 Kafka event payload를 유지한다.
- DLQ header에는 원본 topic, partition, offset, exception class만 남긴다.
- exception stack trace, exception message, secret은 DLQ header에 남기지 않는다.
- 외부 URL 호출 실패는 Pingbell 내부 실패가 아니라 개별 monitor의 check 실패로 기록한다.
- 알림 발송 실패는 check 결과 저장과 incident 판정을 롤백하지 않는다.
- 알림 실패는 `NotificationHistory` 상태로 추적한다.
- retryable 알림 실패는 `RETRY_PENDING`으로 두고 Scheduler가 재시도한다.
- non-retryable 알림 실패 또는 retry exhausted는 `FAILED`로 둔다.
- 수동 재전송은 기존 실패 이력을 덮어쓰지 않고 새 이력을 만든다.

## 현재 DLQ topic

```text
pingbell.health-check.requested.dlq
pingbell.health-check.completed.dlq
pingbell.notification.requested.dlq
```

## 현재 consumer non-retryable exception

```text
IllegalArgumentException
ClassCastException
NoSuchElementException
```

## 용어 정의

| 용어 | 설명 |
| --- | --- |
| retryable | 같은 입력을 나중에 다시 처리하면 성공할 가능성이 있는 실패 |
| non-retryable | 재시도해도 성공 가능성이 낮고 데이터 수정이나 운영 조치가 필요한 실패 |
| retry exhausted | retryable 실패를 최대 횟수까지 재시도했지만 성공하지 못한 상태 |
| DLQ | 자동 처리에서 분리해 운영자가 확인하거나 수동 재처리할 실패 이벤트 저장소 |
| automatic reprocessing | 시스템이 backoff 기준에 따라 자동으로 다시 처리하는 방식 |
| manual reprocessing | 운영자 또는 사용자의 명시적 액션으로 다시 처리하는 방식 |

## 현재 구조를 유지하는 이유

- 현재 MVP 기능은 단일 Spring Boot 앱에서 동작한다.
- 알림 재시도와 수동 재전송은 이미 `NotificationHistory` 상태 기반으로 검증 가능하다.
- 별도 Worker 앱을 먼저 분리하면 장애 판정과 알림 정책보다 운영 복잡도가 커진다.
- DLQ 수동 재처리는 dry-run과 idempotency 검증 없이 구현하면 위험하다.
- 지금은 각 단계의 retryable / non-retryable 기준, secret 제외 기준, 수동 확인 절차를 먼저 고정하는 것이 더 중요하다.

## 이번 정책에서 제외한 일

- DLQ table을 만들지 않는다.
- 별도 Worker 애플리케이션을 만들지 않는다.
- 자동 재처리 스케줄러를 만들지 않는다.
- DB schema를 변경하지 않는다.
- 운영 대시보드나 metric collector를 구현하지 않는다.
- batch 재처리 command를 만들지 않는다.
- DLQ command 로그를 DB table에 저장하지 않는다.

