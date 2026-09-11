# Pingbell Implementation Priority

작성일: 2026-06-29
갱신: 2026-09-10 — 5.2절 "단일 앱 최소 관측성 지표 구현 범위 재검토"는 대부분 구현 완료(`docs/operations/observability-metrics.md` 15절 참고). 이 문서의 다른 항목은 재검토하지 않았으니 진행 전 실제 코드 상태를 다시 확인할 것.

## 1. 목적

이 문서는 현재 구현 완료 기능과 운영 설계 문서만 완료된 기능을 구분하고, 다음 구현 우선순위를 작은 이슈 단위로 정리한다.

이번 작업은 PM 문서 작업이다. Backend / Frontend 코드 구현, Kafka / Worker / DLQ 구현, Prometheus / Grafana 구성은 하지 않는다.

## 2. 현재 구현 완료 범위

현재 구현 완료로 볼 수 있는 범위:

- 회원가입 / 로그인
- JWT 인증
- monitor 등록 / 조회 / 수정 / 일시정지 / 재활성화 / soft delete
- Scheduler 기반 health check
- `CheckResult` 저장
- incident open / resolve 판정
- EMAIL / Slack / Discord webhook 알림 발송
- 알림 채널 등록 / 조회 / 수정 / 비활성화 / 재활성화
- notification target 암호화와 masked target 응답
- `NotificationHistory` 조회
- retryable 알림 실패 자동 재시도
- 실패 알림 수동 재전송
- 기본 대시보드와 프론트 화면
- Docker Compose 기반 PostgreSQL / Mailpit 로컬 실행

## 3. 문서 설계만 완료된 범위

다음은 설계 문서만 완료된 범위이며, 현재 런타임 구현으로 표현하면 안 된다.

- Kafka 기반 이벤트 처리
- Check Worker 분리
- Incident Detector 분리
- Notification Worker 분리
- Notification Sender Worker 분리
- DLQ topic 또는 DLQ table
- Worker 이벤트 자동 재처리
- Prometheus / Grafana 구성
- Micrometer custom metric
- alert rule / dashboard
- Kubernetes / MSA 분리

## 4. 우선순위 판단 기준

다음 작업은 아래 기준으로 고른다.

1. 현재 단일 Spring Boot 앱 구조에서 바로 검증할 수 있는가?
2. 사용자 경험 또는 운영 안정성에 직접 도움이 되는가?
3. 작업 범위가 GitHub Issue 하나로 작게 끝나는가?
4. Kafka / Worker / DLQ / Kubernetes를 선행 요구하지 않는가?
5. 완료 조건과 테스트 방법이 명확한가?

## 5. 우선순위 그룹

### 5.1 MVP 안정화

현재 기능을 더 안전하게 만들거나 사용자가 헷갈리지 않게 하는 작업이다.

| 우선순위 | 작업 | 담당 | 이유 |
| --- | --- | --- | --- |
| 1 | ~~알림 채널 목록에서 enabled / disabled 상태 필터 제공~~ — 2026-09-10 완료 | Frontend | `NotificationChannelPage`에 전체/활성/비활성 필터 구현·브라우저 검증 완료. |
| 2 | 알림 이력 상태 표시 UX 개선 — 2026-09-11 재확인, 대부분 이미 구현됨 | Frontend | `STATUS_FILTERS` 버튼, `notificationStatusMeta`(라벨+톤), `FAILED`일 때 재전송 버튼까지 이미 있음. 남은 진짜 gap은 `failureType`(채널비활성/발송실패/재시도초과) 미노출뿐 — `docs/next_feature_request.md`로 분리해 진행 중. |
| 3 | monitor 수정 / 일시정지 / 삭제 UX 정리 — 2026-09-11 재확인, 대부분 이미 구현됨 | Frontend | `MonitorDetailPage`/`MonitorListPage`에 일시정지↔재활성화 토글, 삭제 `ConfirmModal`, 에러 메시지까지 이미 있음. 추가로 다듬을 부분이 있는지는 화면으로 직접 확인 필요. |
| 4 | slow response 표시 정책 점검 — 2026-09-11 재확인, UI 표시는 이미 구현됨 | Backend / Frontend | `checkStatusMeta`에 `SLOW_RESPONSE`→"응답 지연"(warning) 라벨 이미 있음. 남은 건 "이게 Incident 판정에 영향을 줘야 하는가"라는 정책 질문(PM 판단 필요) — 순수 Frontend 작업은 아님. |

### 5.2 운영성 개선

단일 앱 구조를 유지하면서 운영 판단에 도움이 되는 작업이다.

| 우선순위 | 작업 | 담당 | 이유 |
| --- | --- | --- | --- |
| 1 | ~~단일 앱 최소 관측성 지표 구현 범위 재검토~~ — 2026-09-10 대부분 완료 | Backend / Infra | `PingbellMetrics`로 health check/incident/notification counter·gauge 6종 구현 완료. 남은 건 `retry_due_current`/`retry_exhausted_total` 정도(`docs/operations/observability-metrics.md` 17절). |
| 2 | notification retry exhausted 조회 / 표시 개선 | Backend / Frontend | 운영자 또는 사용자가 개입해야 할 실패를 찾기 쉽게 한다. |
| 3 | 장애 지속 시간 표시 개선 | Backend / Frontend | incident open부터 resolved까지의 시간을 사용자에게 설명하기 쉽다. |

### 5.3 고도화 설계

현재 바로 구현하지 않고, 트래픽이나 운영 필요가 생긴 뒤 진행할 작업이다.

| 우선순위 | 작업 | 담당 | 이유 |
| --- | --- | --- | --- |
| 1 | Kafka 도입 시점 판단 | PM / Infra | 단일 앱으로 감당하기 어려운 부하나 처리 지연이 먼저 확인되어야 한다. |
| 2 | Check Worker 구현 | Backend / Infra | `docs/architecture/check-worker-design.md` 기준이 있으나 Kafka/outbox 선행 검토가 필요하다. |
| 3 | Incident Detector 구현 | Backend / Infra | idempotency 저장소나 outbox 기준이 먼저 필요하다. |
| 4 | Notification Worker / Sender Worker 구현 | Backend / Infra | 중복 발송 방지와 retry/DLQ 저장 기준이 먼저 필요하다. |
| 5 | Prometheus / Grafana 구성 | Infra | 관측성 지표 목록을 기준으로 실제 metric 구현 후 진행하는 편이 안전하다. |

## 6. 다음에 바로 진행할 작업

가장 적절한 다음 작업은 `알림 채널 목록 enabled / disabled 필터 제공`이다.

선정 이유:

- 현재 단일 앱 구조에서 바로 구현 가능하다.
- Kafka / Worker / DLQ / Prometheus가 필요 없다.
- 사용자가 실제 발송 대상 채널과 비활성 채널을 구분하기 쉬워진다.
- Frontend 중심의 작은 이슈로 끝낼 수 있다.
- 기존 API가 `enabled` 값을 응답하고 있다면 백엔드 변경 없이 가능성이 높다.

## 7. 다음 작업 완료 조건

작업명: 알림 채널 목록 enabled / disabled 필터 제공

담당 에이전트:

- Frontend Agent
- Backend Agent 보조 검토 가능

작업 범위:

- 알림 채널 목록 화면에 필터 UI를 추가한다.
- 필터 옵션은 `전체`, `활성`, `비활성`으로 둔다.
- 기본값은 `전체` 또는 현재 UX에 맞는 값으로 선택하되, 사용자가 현재 발송 대상 채널을 쉽게 볼 수 있어야 한다.
- `enabled=false` 채널은 비활성 상태임을 명확히 표시한다.
- API 응답에 `enabled`가 이미 포함되어 있으면 프론트에서 필터링한다.
- API 응답에 `enabled`가 없다면 Backend 보완 이슈로 분리한다.

제외 범위:

- 알림 채널 DB schema 변경
- 알림 채널 삭제 정책 변경
- 알림 발송 로직 변경
- Slack / Discord OAuth
- Kafka / Worker / DLQ

테스트 기준:

- 전체 필터에서 모든 채널이 보인다.
- 활성 필터에서 `enabled=true` 채널만 보인다.
- 비활성 필터에서 `enabled=false` 채널만 보인다.
- 비활성 채널의 target 원문은 노출되지 않고 기존 masked target 정책을 유지한다.
- 프론트 빌드가 통과한다.

## 8. 다음 agent에게 넘길 프롬프트

```text
AGENTS.md를 보고 docs/next_feature_request.md를 진행해줘.

이번 작업은 Frontend 작업이야.
알림 채널 목록 화면에 enabled / disabled 필터를 추가해줘.

조건:
- Kafka / Worker / DLQ / Prometheus는 구현하지 않는다.
- 알림 발송 로직은 변경하지 않는다.
- target 원문은 노출하지 않는다.
- API 응답에 enabled가 있으면 프론트에서 필터링한다.
- API 응답에 enabled가 없으면 백엔드 변경이 필요한지 확인하고 최소 범위로 처리한다.
- 완료 후 테스트 방법을 정리한다.
```

## 9. 다음 문서 작업 후보

이번 우선순위 정리 이후 문서 작업이 필요하다면 다음 순서가 적절하다.

1. `docs/status/project-status` 최신 번호 갱신
2. README의 다음 작업 후보를 구현 완료 상황에 맞게 갱신
3. 실제 구현이 끝난 뒤 PR 템플릿 갱신

## 10. 이번 문서에서 하지 않은 일

- Frontend 코드를 수정하지 않았다.
- Backend 코드를 수정하지 않았다.
- DB schema를 변경하지 않았다.
- Kafka / Worker / DLQ를 구현하지 않았다.
- Prometheus / Grafana를 구성하지 않았다.
