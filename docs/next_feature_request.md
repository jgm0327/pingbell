---
name: Feature Request
about: 다음 개발 작업 요청 기록
title: "[Frontend] 알림 발송 실패 이력에 실패 유형(failureType) 필터 노출"
labels: frontend
assignees: ''
---

## 작업 목적

알림 채널 목록 활성/비활성 필터(이전 작업)를 끝내면서 알림 발송 이력 쪽을 확인해보니, 백엔드는 이미 실패를 세 종류로 구분해 조회할 수 있는 계약을 갖고 있는데(`NotificationHistoryRepository.findRetryExhaustedFailuresByChannelMemberId`, `findChannelDisabledFailuresByChannelMemberId`, `findSendFailedFailuresByChannelMemberId`, 그리고 프론트 `NotificationFailureType = 'CHANNEL_DISABLED' | 'SEND_FAILED' | 'RETRY_EXHAUSTED'`), 프론트 `NotificationChannelPage`는 `status`(대기중/재시도대기/발송완료/실패)만 필터링하고 `failureType`은 API 타입에만 존재할 뿐 화면에 전혀 안 쓰인다.

`docs/planning/implementation-priority.md` 5.2절의 "notification retry exhausted 조회/표시 개선"이 바로 이 gap을 가리키고 있다. 실패(`FAILED`) 이력 안에서 "재시도까지 다 소진하고 최종 실패한 것"과 "채널이 비활성이라 애초에 안 보낸 것"과 "그냥 한 번 실패한 것"을 운영자(=이 서비스에서는 사용자 본인)가 구분하지 못하면, 재시도 대상이 아닌 것까지 매번 눈으로 훑어야 한다.

## 작업 내용

- [ ] 알림 발송 이력 필터에 `failureType` 옵션을 추가한다(`전체`/`채널 비활성`/`발송 실패`/`재시도 초과`), 기존 `status` 필터와 별개로 또는 `FAILED` 선택 시에만 하위 필터로 노출한다(UX는 구현 시 판단).
- [ ] 재시도 초과(`RETRY_EXHAUSTED`) 이력은 "더 이상 자동 재시도되지 않는다"는 것을 배지나 문구로 명확히 표시한다.
- [ ] `useNotificationHistories`는 이미 `failureType` 파라미터를 받고 있으니, 화면에서 그 값을 실제로 넘기기만 하면 된다(백엔드/훅 변경 불필요, 우선 코드로 재확인할 것).

## 완료 조건

- [ ] 세 가지 `failureType`로 각각 필터링했을 때 해당하는 이력만 보인다.
- [ ] `재시도 초과` 이력과 `채널 비활성` 이력이 시각적으로 구분된다.
- [ ] 기존 `status` 필터, 재전송 버튼 동작은 그대로 유지된다.
- [ ] 프론트 빌드가 통과한다.

## 제외 범위

- 백엔드 API/쿼리 변경 (이미 존재함 — 진행 전 실제로 다시 확인할 것, 이 프로젝트에서 "이미 되어 있는데 문서만 안 됐다고 되어 있는" 경우가 두 번 있었다)
- 재시도 정책/횟수 변경
- Kafka / Worker / DLQ / Prometheus

## 테스트 기준

- 세 가지 필터 각각에서 올바른 이력만 보이는지 확인.
- 재전송 버튼이 `FAILED` 상태에서 기존과 동일하게 동작하는지 확인.

## 담당 에이전트

- 주 담당: Frontend Agent

## 다음 작업

- 이후에는 `docs/planning/implementation-priority.md` 5.1절의 나머지 항목(monitor 수정/일시정지/삭제 UX 정리, slow response 표시 정책 점검)이나, `docs/operations/observability-metrics.md` 17절의 `retry_due_current`/`retry_exhausted_total` gauge를 검토한다.

## 다음 에이전트에게 전달할 프롬프트

```text
AGENTS.md와 docs/agents/frontend-agent.md를 읽고 docs/next_feature_request.md(알림 발송 실패 이력 failureType 필터)를 진행해줘.

조건:
- 시작 전에 NotificationHistoryPage/queries.ts/api.ts를 직접 읽고, 이 기능이 이미 구현되어 있지 않은지 먼저 확인해줘(이 프로젝트에서 계획 문서가 실제 코드보다 낡아있던 적이 이미 두 번 있었다).
- 백엔드 API는 이미 failureType을 지원하니 새로 만들지 않는다.
- 재시도 초과와 채널 비활성 실패를 시각적으로 구분한다.
- 완료 후 테스트 방법을 정리한다.
```
