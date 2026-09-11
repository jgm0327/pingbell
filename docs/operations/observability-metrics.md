# Pingbell Observability Metrics

작성일: 2026-06-29
갱신: 2026-09-10 — 4·6.1·7절의 counter/timer 지표와 12절의 gauge 2종이 실제로 구현됨(15~17절 참고). Prometheus/Grafana는 여전히 미구현.

## 1. 목적

이 문서는 Pingbell의 현재 단일 Spring Boot 앱 구조와 미래 Worker 분리 구조에서 필요한 최소 관측성 지표 후보를 정리한다.

이번 작업은 설계 문서 작업으로 시작했다. 2026-09-10 기준으로 4·6.1·7절의 counter/timer 후보와 12절의 gauge 2종은 `PingbellMetrics`로 구현되어 있다(15절 참고). Prometheus, Grafana, log collector, dashboard, alert rule은 여전히 구현하지 않는다.

## 2. 현재 관측성 원칙

현재 단계에서는 운영 복잡도를 늘리지 않고, 어떤 상태를 봐야 하는지 먼저 정리한다.

원칙:

- MVP 단계에서는 단일 앱에서 확인 가능한 최소 지표를 우선한다.
- Kafka, Worker, DLQ 지표는 미래 확장 지표로 분리한다.
- 지표 label에는 cardinality가 큰 값과 secret을 넣지 않는다.
- monitor URL, webhook URL, email address 원문은 metric label과 log field에 넣지 않는다.
- 사용자 서버 장애와 Pingbell 내부 처리 실패를 구분한다.
- 알림 실패는 monitor 장애가 아니라 notification 상태로 관측한다.

## 3. 지표 분류

지표는 다음 범주로 나눈다.

| 범주 | 목적 |
| --- | --- |
| Health Check | URL 호출 결과와 응답 시간 확인 |
| Monitor State | monitor 상태와 예약 지연 확인 |
| Incident | 장애 발생, 복구, 지속 시간 확인 |
| Notification | 알림 발송 성공, 실패, 재시도 상태 확인 |
| Retry / DLQ | 재처리 대기, 최종 실패, 운영 개입 대상 확인 |
| Worker Future | 미래 Worker 분리 이후 처리 지연과 실패 확인 |

## 4. Health Check 지표

### 4.1 현재 단일 앱 최소 지표

| 지표명 후보 | 타입 | 설명 |
| --- | --- | --- |
| `pingbell_health_check_total` | counter | health check 실행 횟수 |
| `pingbell_health_check_success_total` | counter | 성공 check 횟수 |
| `pingbell_health_check_failure_total` | counter | 실패 check 횟수 |
| `pingbell_health_check_timeout_total` | counter | timeout check 횟수 |
| `pingbell_health_check_http_error_total` | counter | 4xx / 5xx check 횟수 |
| `pingbell_health_check_slow_response_total` | counter | timeout 기준보다 느린 응답 횟수 |
| `pingbell_health_check_response_time_ms` | histogram | check 응답 시간 분포 |

권장 label:

- `status`: `SUCCESS`, `FAILURE`, `TIMEOUT`, `HTTP_ERROR`, `SLOW_RESPONSE`
- `http_status_family`: `2xx`, `3xx`, `4xx`, `5xx`, `none`

주의:

- `monitorId`는 label로 넣지 않는 편이 안전하다. monitor 수가 늘어나면 cardinality가 커진다.
- monitor URL 원문은 절대 label에 넣지 않는다.
- 사용자별 지표가 필요하면 metric label보다 DB query나 별도 admin 화면을 우선 검토한다.

**구현 상태(2026-09-10)**: `PingbellMetrics.recordHealthCheck()`가 `CheckService`에서 호출된다. 위 표의 6개 counter 후보를 각각 별도 metric으로 만들지 않고, 하나의 `pingbell.health.check.total` counter에 `status`(`SUCCESS`/`FAILURE`/`TIMEOUT`/`HTTP_ERROR`/`SLOW_RESPONSE`)와 `http_status_family`(`2xx`~`5xx`, `none`) label로 구분했다 — Prometheus/Grafana에서도 label로 쪼개 보는 편이 metric 개수를 늘리는 것보다 일반적인 방식이라 이 방향으로 정했다. 응답 시간은 `pingbell.health.check.response.time`(timer, 같은 label)로 기록된다. `/actuator/metrics/pingbell.health.check.total`로 바로 조회 가능.

### 4.2 응답 시간 p95 / p99

응답 시간은 histogram 기반으로 p95 / p99를 계산하는 방향을 권장한다.

후보:

```text
histogram_quantile(0.95, pingbell_health_check_response_time_ms)
histogram_quantile(0.99, pingbell_health_check_response_time_ms)
```

현재 단일 앱에서는 먼저 DB의 `CheckResult.responseTimeMs`를 기준으로 화면 또는 쿼리에서 확인할 수 있다. Prometheus histogram은 운영 고도화 단계에서 도입한다.

## 5. Monitor State 지표

| 지표명 후보 | 타입 | 설명 |
| --- | --- | --- |
| `pingbell_monitor_status_total` | gauge | 상태별 monitor 수 |
| `pingbell_monitor_due_total` | gauge | 현재 시각 기준 check 대상 monitor 수 |
| `pingbell_monitor_check_lag_seconds` | histogram | `nextCheckAt` 대비 실제 처리 지연 |

권장 label:

- `status`: `ACTIVE`, `DOWN`, `PAUSED`

주의:

- `monitorId`, `name`, `url`은 label로 넣지 않는다.
- lag는 scheduler 또는 worker backlog를 파악하기 위한 지표다.

## 6. Incident 지표

### 6.1 현재 단일 앱 최소 지표

| 지표명 후보 | 타입 | 설명 |
| --- | --- | --- |
| `pingbell_incident_opened_total` | counter | incident open 횟수 |
| `pingbell_incident_resolved_total` | counter | incident resolved 횟수 |
| `pingbell_incident_open_current` | gauge | 현재 open incident 수 |
| `pingbell_incident_duration_seconds` | histogram | incident 지속 시간 분포 |

권장 label:

- `result`: `opened`, `resolved`

주의:

- monitor URL, monitor name은 label에 넣지 않는다.
- member id도 기본 label로 넣지 않는다.
- 사용자별 incident 분석은 DB 기반 관리 화면이나 batch report를 우선 고려한다.

**구현 상태(2026-09-10)**:
- `pingbell.incident.total`(counter, `result`=`opened`/`resolved`)이 `IncidentDetectionService`에서 기록된다.
- `pingbell.incident.open.current`(gauge)가 추가로 구현됐다 — `IncidentRepository.countByStatus(OPEN)`을 조회 시점마다 다시 세는 방식으로, 전체 tenant 합계다(memberId로 스코프하지 않음. tenant별 값이 필요하면 기존 `countByMonitorMemberIdAndStatus`를 쓰는 관리 화면 API를 별도로 쓴다).
- `pingbell_incident_open_current` 후보명 그대로 사용했다. `pingbell_incident_due_total`, `pingbell_monitor_check_lag_seconds`, `pingbell_incident_duration_seconds`(histogram)는 아직 미구현.

### 6.2 운영 관점

incident 지표로 확인할 질문:

- 장애가 갑자기 많이 발생했는가?
- 복구되지 않고 오래 열린 incident가 있는가?
- 장애 지속 시간이 길어지고 있는가?
- check failure 증가와 incident open 증가가 함께 발생하는가?

## 7. Notification 지표

### 7.1 현재 단일 앱 최소 지표

| 지표명 후보 | 타입 | 설명 |
| --- | --- | --- |
| `pingbell_notification_sent_total` | counter | 알림 발송 성공 횟수 |
| `pingbell_notification_failed_total` | counter | 알림 최종 실패 횟수 |
| `pingbell_notification_retry_pending_current` | gauge | 재시도 대기 중인 알림 이력 수 |
| `pingbell_notification_retry_attempt_total` | counter | 알림 재시도 시도 횟수 |
| `pingbell_notification_retry_exhausted_total` | counter | 최대 재시도 초과 횟수 |
| `pingbell_notification_send_duration_seconds` | histogram | sender 발송 소요 시간 |

권장 label:

- `channel_type`: `EMAIL`, `SLACK`, `DISCORD`
- `notification_type`: `INCIDENT_OPEN`, `INCIDENT_RESOLVED`
- `status`: `SENT`, `FAILED`, `RETRY_PENDING`
- `failure_type`: `retryable`, `non_retryable`, `retry_exhausted`

주의:

- email address, webhook URL, maskedTarget도 label로 넣지 않는다.
- `errorMessage`는 label로 넣지 않는다. cardinality가 크고 secret이 섞일 수 있다.
- 실패 원인은 낮은 cardinality의 `failure_type` 정도로 묶는다.

**구현 상태(2026-09-10)**: `pingbell.notification.delivery.total`(counter, `channel_type`/`notification_type`/`status`/`manual_resend`)과 `pingbell.notification.retry.attempt.total`(counter, `channel_type`/`notification_type`/`result_status`)이 `NotificationService`·`NotificationRetryService`·`NotificationHistoryResendService`에서 기록된다. `send_duration_seconds` histogram과 개별 `sent_total`/`failed_total` counter로 분리하는 대신, `status` label 하나로 성공/실패/재시도대기를 구분하는 방식을 택했다.

### 7.2 수동 재전송 지표

| 지표명 후보 | 타입 | 설명 |
| --- | --- | --- |
| `pingbell_notification_manual_resend_total` | counter | 수동 재전송 요청 횟수 |
| `pingbell_notification_manual_resend_success_total` | counter | 수동 재전송 성공 횟수 |
| `pingbell_notification_manual_resend_failed_total` | counter | 수동 재전송 실패 횟수 |

권장 label:

- `channel_type`
- `notification_type`
- `result`: `success`, `failed`, `retry_pending`

## 8. Retry / DLQ 지표

현재 단일 앱에서는 DLQ가 없으므로 notification retry 상태를 먼저 본다. DLQ 지표는 미래 확장 지표로 둔다.

### 8.1 현재 단일 앱

| 지표명 후보 | 타입 | 설명 |
| --- | --- | --- |
| `pingbell_retry_pending_current` | gauge | 전체 재시도 대기 이력 수 |
| `pingbell_retry_due_current` | gauge | 현재 재시도 대상 이력 수 |
| `pingbell_retry_exhausted_total` | counter | 재시도 초과로 최종 실패한 수 |

**구현 상태(2026-09-10)**: `pingbell.notification.retry.pending.current`(gauge)가 구현됐다 — `NotificationHistoryRepository.countByStatus(RETRY_PENDING)`을 조회 시점마다 다시 세는 방식으로, `pingbell_retry_pending_current` 후보와 같은 목적이다(전체 tenant 합계, memberId로 스코프하지 않음). `retry_due_current`와 `retry_exhausted_total`은 아직 미구현 — `NotificationHistoryRepository.findRetryDueHistories()`가 이미 있으니 다음 단계에서 그 결과 크기를 gauge로 노출하면 된다.

### 8.2 미래 DLQ

| 지표명 후보 | 타입 | 설명 |
| --- | --- | --- |
| `pingbell_dlq_events_total` | counter | DLQ로 분리된 이벤트 수 |
| `pingbell_dlq_events_current` | gauge | 현재 DLQ에 남아 있는 이벤트 수 |
| `pingbell_dlq_oldest_event_age_seconds` | gauge | 가장 오래된 DLQ 이벤트 age |
| `pingbell_dlq_manual_reprocess_total` | counter | 수동 재처리 요청 횟수 |
| `pingbell_dlq_manual_reprocess_success_total` | counter | 수동 재처리 성공 횟수 |
| `pingbell_dlq_manual_reprocess_failed_total` | counter | 수동 재처리 실패 횟수 |

권장 label:

- `event_type`: `HealthCheckRequested`, `HealthCheckCompleted`, `IncidentOpened`, `IncidentResolved`, `NotificationRequested`
- `failure_stage`: `check_worker`, `incident_detector`, `notification_worker`, `notification_sender`
- `failure_type`: `schema_error`, `data_mismatch`, `retry_exhausted`, `unsupported_channel`, `decryption_failed`

주의:

- original payload 전체를 label에 넣지 않는다.
- event id도 label로 넣지 않는다. trace/log correlation용 field로만 둔다.

## 9. Worker 분리 이후 지표

Worker 분리 이후에는 처리량, 지연, 실패를 별도로 본다.

| 지표명 후보 | 타입 | 설명 |
| --- | --- | --- |
| `pingbell_worker_event_consumed_total` | counter | worker가 consume한 이벤트 수 |
| `pingbell_worker_event_processed_total` | counter | worker 처리 성공 이벤트 수 |
| `pingbell_worker_event_failed_total` | counter | worker 처리 실패 이벤트 수 |
| `pingbell_worker_processing_duration_seconds` | histogram | worker 처리 시간 |
| `pingbell_worker_event_lag_seconds` | histogram | 이벤트 발생 시각 대비 처리 지연 |
| `pingbell_worker_retry_total` | counter | worker 이벤트 재처리 횟수 |

권장 label:

- `worker`: `check_worker`, `incident_detector`, `notification_worker`, `notification_sender`
- `event_type`
- `result`: `success`, `failed`, `skipped`

주의:

- Kafka partition, offset 같은 상세 값은 metric label보다 log field로 남긴다.
- consumer group은 label로 사용할 수 있지만 너무 자주 바뀌지 않게 한다.

## 10. Label 기준

### 10.1 넣어도 되는 label

- enum 성격의 상태 값
- channel type
- notification type
- check status
- HTTP status family
- worker name
- event type
- failure stage
- 낮은 cardinality의 failure type

### 10.2 넣지 말아야 할 label

- monitor URL
- webhook URL
- email address
- user name
- monitor name
- raw error message
- JWT 또는 API key
- event id
- notification history id
- incident id
- monitor id

이유:

- secret이 노출될 수 있다.
- cardinality가 커져 metric 저장 비용과 조회 비용이 커진다.
- 개인 식별 정보가 운영 도구에 과도하게 남을 수 있다.

## 11. 로그 기준

metric label에는 넣지 않더라도 로그에는 추적용 id를 남길 수 있다.

운영 로그에 남길 수 있는 값:

- `monitorId`
- `checkResultId`
- `incidentId`
- `notificationHistoryId`
- `channelId`
- `eventId`
- `eventType`
- `failureStage`
- redacted error message

로그에도 남기지 않을 값:

- monitor URL 원문
- webhook URL
- email address 원문
- 복호화된 notification target
- Authorization header
- token, password, API key

## 12. 현재 단일 앱에서 먼저 볼 지표

MVP 이후 가장 먼저 볼 최소 지표:

1. health check status별 count — 구현됨(`pingbell.health.check.total`)
2. health check response time p95 / p99 — timer는 구현됨(`pingbell.health.check.response.time`), p95/p99 계산은 Prometheus histogram_quantile 도입 전이라 미구현
3. current open incident count — 구현됨(`pingbell.incident.open.current`, gauge)
4. incident opened / resolved count — 구현됨(`pingbell.incident.total`)
5. notification sent / failed count by channel type — 구현됨(`pingbell.notification.delivery.total`)
6. retry pending count — 구현됨(`pingbell.notification.retry.pending.current`, gauge)
7. retry exhausted count — 미구현

이 지표들은 현재 DB와 service 흐름만으로도 의미가 있다. Prometheus를 붙이기 전에는 관리자용 쿼리, 로그, 간단한 actuator 지표 확장 후보로만 둔다. 7개 중 6개가 `PingbellMetrics`로 구현됐고(2026-09-10), `/actuator/metrics/{지표명}`으로 바로 조회할 수 있다.

## 13. 미래 확장 순서

권장 순서:

1. 현재 단일 앱에서 DB 기반 운영 쿼리와 로그 기준 정리
2. Actuator / Micrometer 기본 지표 확인
3. health check / notification custom metric 추가
4. Prometheus / Grafana 구성
5. Worker 분리 후 event lag / processing failure / DLQ 지표 추가
6. alert rule과 dashboard 작성

이번 문서에서는 1단계의 지표 목록 정리까지만 수행한다.

## 14. 포트폴리오에 설명할 운영 관점

포트폴리오에 남길 포인트:

- 사용자 서버 장애와 Pingbell 내부 처리 실패를 분리해 관측한다.
- check failure count와 incident open count를 분리해 일시적 실패와 실제 장애를 구분한다.
- notification 실패는 monitor 장애가 아니라 알림 채널 또는 외부 알림 서비스 문제로 본다.
- retry pending, retry exhausted, DLQ 후보를 통해 운영자가 개입해야 할 실패를 구분한다.
- metric label에 URL, webhook, email address를 넣지 않는 보안 기준을 세웠다.
- Worker 분리 전에도 어떤 지표를 봐야 하는지 먼저 정리해 이후 Prometheus/Grafana 도입 범위를 좁혔다.

## 15. 구현 현황 (2026-09-10 갱신)

이 문서는 원래 설계 전용 문서로 시작했지만(2026-06-29), 이후 세션에서 `PingbellMetrics`(`com.monit.pingbell.global.observability`)로 4·6.1·7·8.1절의 counter/timer/gauge 후보 대부분이 실제 구현됐다.

구현된 것:

- Health Check: `pingbell.health.check.total`(counter), `pingbell.health.check.response.time`(timer) — `CheckService`
- Incident: `pingbell.incident.total`(counter), `pingbell.incident.open.current`(gauge) — `IncidentDetectionService`
- Notification: `pingbell.notification.delivery.total`(counter), `pingbell.notification.retry.attempt.total`(counter), `pingbell.notification.retry.pending.current`(gauge) — `NotificationService`, `NotificationRetryService`, `NotificationHistoryResendService`
- `application.yml`의 `management.endpoints.web.exposure.include: health,metrics`로 `/actuator/metrics/{지표명}` 조회 가능
- 테스트: `PingbellMetricsTest`(gauge가 등록 시점 값을 캐시하지 않고 조회마다 재계산되는지), `IncidentRepositoryTest`/`NotificationHistoryRepositoryTest`(전역 count가 tenant 스코프 없이 전체 합계인지)

여전히 구현하지 않은 것:

- Prometheus / Grafana / dashboard / alert rule
- `retry_due_current`, `retry_exhausted_total` gauge/counter (8.1절 나머지 후보)
- Monitor State(5절), DLQ(8.2절), Worker 분리 이후(9절) 지표 — 아직 해당 기능 자체가 없음

이유(여전히 유효):

- MVP 기능과 알림 정책 안정화가 우선이다.
- Prometheus/Grafana 도입은 실제 운영 트래픽이나 필요가 생긴 뒤 판단한다(AGENTS.md).
- 지금 구현된 counter/gauge만으로도 `/actuator/metrics`와 로그로 최소 관측이 가능하다.

## 16. 이번 문서에서 하지 않은 일 (2026-09-10 시점)

- Prometheus를 구성하지 않았다.
- Grafana dashboard를 만들지 않았다.
- alert rule을 작성하지 않았다.
- log collector를 구성하지 않았다.
- `retry_due_current`, `retry_exhausted_total`, Monitor State, DLQ, Worker 지표는 구현하지 않았다.
- DB schema를 변경하지 않았다(gauge는 기존 테이블을 조회만 한다).

## 17. 다음 작업

다음 이슈 후보:

- `retry_due_current`(gauge), `retry_exhausted_total`(counter) 추가 — `NotificationHistoryRepository.findRetryDueHistories()`와 기존 retry-exhausted 조회 로직을 재사용할 수 있다.
- README / 포트폴리오 문서에 이번 관측성 구현 반영(현재 구현된 기능과 설계 전용 범위를 명확히 구분).
