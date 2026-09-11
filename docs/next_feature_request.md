---
name: Feature Request
about: 다음 개발 작업 요청 기록
title: "[Backend] 재시도 대기/초과 관측성 gauge·counter 추가 (retry_due_current, retry_exhausted_total)"
labels: backend
assignees: ''
---

## 작업 목적

`docs/operations/observability-metrics.md` 8.1절 후보 중 `retry_pending_current`는 이미 구현됐고(`PingbellMetrics.registerStateGauges`), `retry_due_current`(gauge)와 `retry_exhausted_total`(counter)만 남아 있다. 알림 채널/발송 실패 이력 필터 작업(Frontend, 완료)과 짝을 이루는 Backend observability 마무리 작업이다.

`NotificationHistoryRepository.findRetryDueHistories(now)`가 이미 "지금 재시도 대상인 이력" 목록을 반환하므로, 그 크기를 gauge로 노출하기만 하면 된다. `retry_exhausted_total`은 재시도를 다 소진하고 최종 실패로 전환되는 시점(`NotificationRetryService` 또는 관련 서비스가 `maxRetryCount`에 도달해 더 이상 재시도하지 않기로 판단하는 지점)에 counter를 1 증가시키면 된다.

## 작업 내용

- [ ] `NotificationHistoryRepository`에 재시도 대상 건수를 세는 메서드 추가(`findRetryDueHistories`를 그대로 재사용하거나, count 전용 쿼리를 새로 추가 — 목록 전체를 안 가져와도 되면 count 쿼리가 더 가볍다).
- [ ] `PingbellMetrics.registerStateGauges()`에 `pingbell.notification.retry.due.current` gauge 추가(다른 gauge와 동일한 패턴: 조회 시점마다 repository를 다시 센다).
- [ ] 재시도가 `maxRetryCount`에 도달해 더 이상 재시도하지 않기로 확정되는 지점을 찾아 `pingbell.notification.retry.exhausted.total` counter를 1 증가시킨다(어디가 그 지점인지 `NotificationRetryService`/`NotificationHistory` 상태 전이를 먼저 코드로 확인할 것).

## 완료 조건

- [ ] `/actuator/metrics/pingbell.notification.retry.due.current`, `/actuator/metrics/pingbell.notification.retry.exhausted.total`로 조회 가능.
- [ ] gauge는 이벤트 시점이 아니라 조회 시점마다 재계산된다(기존 gauge 2종과 동일한 검증 패턴 — `PingbellMetricsTest` 참고).
- [ ] counter는 재시도 소진으로 최종 실패 처리되는 경우에만 증가하고, 일반 실패(`SEND_FAILED`)나 채널 비활성(`CHANNEL_DISABLED`)에서는 증가하지 않는다.
- [ ] `docs/operations/observability-metrics.md` 8.1·12·17절을 실제 구현 상태로 갱신한다.

## 제외 범위

- Prometheus / Grafana / alert rule
- DLQ 지표(8.2절), Worker 분리 이후 지표(9절) — 아직 해당 기능 자체가 없음
- 재시도 정책/횟수 변경

## 테스트 기준

- `PingbellMetricsTest`에 두 지표 케이스 추가(기존 gauge 테스트와 동일한 방식 — mock repository 값이 바뀌면 다음 조회에 반영되는지).
- counter가 재시도 소진 시점에만 증가하는지 관련 서비스 테스트에서 확인(mock `PingbellMetrics`로 호출 여부/횟수 검증).

## 담당 에이전트

- 주 담당: Backend Agent

## 다음 작업

- 이후에는 `docs/planning/implementation-priority.md` 5.1절의 "monitor 수정/일시정지/삭제 UX 정리"(이미 대부분 구현되어 있어 보이므로, 화면을 직접 열어 정말 남은 게 있는지부터 확인)나 "slow response 표시 정책 점검"(순수 코드 작업이 아니라 PM 판단이 먼저 필요)을 검토한다.

## 다음 에이전트에게 전달할 프롬프트

```text
AGENTS.md와 docs/agents/backend-agent.md를 읽고 docs/next_feature_request.md(재시도 대기/초과 관측성 gauge·counter 추가)를 진행해줘.

조건:
- Prometheus/Grafana는 구성하지 않는다.
- gauge는 이벤트 기록이 아니라 조회 시점마다 재계산되도록 한다(기존 pingbell.incident.open.current, pingbell.notification.retry.pending.current와 동일한 패턴).
- retry_exhausted_total이 정확히 어느 시점에 증가해야 하는지 NotificationRetryService 코드를 먼저 읽고 확인한다.
- 완료 후 docs/operations/observability-metrics.md를 실제 구현 상태로 갱신하고, 테스트 방법을 정리한다.
```
