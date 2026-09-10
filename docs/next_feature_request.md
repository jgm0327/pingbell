---
name: Feature Request
about: 다음 개발 작업 요청 기록
title: "[Frontend] 알림 채널 목록 활성/비활성 필터"
labels: frontend
assignees: ''
---

## 작업 목적

`docs/ai/rag-implementation-issues.md`의 Issue 1~6이 모두 완료되어 RAG 파이프라인(문서 등록 → chunking/embedding → tenant pre-filter 벡터 검색 → 로그 분석 context/reference 연결 → 합성 fixture 기반 전후 품질 평가)이 끝났다. RAG 관련 후속 작업은 `docs/ai/rag-quality-evaluation.md`에 기록한 한계(예: http-5xx 확신도 표현)가 반복 측정으로 재확인될 때 별도 이슈로 다시 판단한다.

Frontend F1~F6도 이미 완료된 상태다. 다음으로 진행할 작업은 `docs/planning/implementation-priority.md`(2026-06-29 작성, 아직 미구현으로 남아있는 항목)에서 가장 우선순위가 높은 MVP 안정화 작업인 알림 채널 목록의 활성/비활성 필터다. Frontend `notification-channel` 기능에는 아직 필터 UI가 없다(채널 행에 `enabled` 값은 표시되지만 목록 필터링 기능은 없음).

## 작업 내용

- [ ] 알림 채널 목록 화면에 필터 UI(`전체`/`활성`/`비활성`)를 추가한다.
- [ ] 기본값은 현재 UX에 맞게 선택하되, 사용자가 실제 발송 대상(활성) 채널을 쉽게 구분할 수 있어야 한다.
- [ ] `enabled=false` 채널은 목록에서 비활성 상태임을 명확히 표시한다(배지 등 기존 스타일 재사용).
- [ ] 채널 목록 API 응답에 `enabled` 필드가 이미 있으면 프론트에서만 필터링한다.
- [ ] API 응답에 `enabled`가 없으면 Backend 보완이 필요한지 확인하고, 필요하면 최소 범위로만 추가한다(계약 변경이면 별도로 명시).

## 완료 조건

- [ ] `전체` 필터에서 모든 채널이 보인다.
- [ ] `활성` 필터에서 `enabled=true` 채널만 보인다.
- [ ] `비활성` 필터에서 `enabled=false` 채널만 보인다.
- [ ] 필터 적용 여부와 무관하게 target 원문은 노출되지 않고 기존 masked target 정책을 유지한다.
- [ ] 프론트 빌드와 관련 테스트가 통과한다.

## 제외 범위

- 알림 채널 DB schema 변경
- 알림 채널 삭제 정책 변경
- 알림 발송 로직 변경
- Slack / Discord OAuth
- Kafka / Worker / DLQ / Prometheus (현재 MVP 단계에서 다루지 않음)

## 테스트 기준

- 각 필터 옵션에서 목록에 보이는 채널 집합을 수동 또는 컴포넌트 테스트로 확인한다.
- 활성/비활성 전환 후 필터 결과가 즉시 갱신되는지 확인한다(기존 채널 활성화/비활성화 mutation과 연동).

## 담당 에이전트

- 주 담당: Frontend Agent
- 협업: Backend Agent (API에 `enabled`가 없을 경우에만)

## 다음 작업

- 이 작업 완료 후에는 `docs/planning/implementation-priority.md` 5.1절의 나머지 MVP 안정화 항목(알림 이력 상태 표시 UX 개선, monitor 수정/일시정지/삭제 UX 정리, slow response 표시 정책 점검)을 순서대로 검토한다.

## 다음 에이전트에게 전달할 프롬프트

```text
AGENTS.md와 docs/agents/frontend-agent.md를 읽고 docs/next_feature_request.md(알림 채널 목록 활성/비활성 필터)를 진행해줘.

조건:
- Kafka / Worker / DLQ / Prometheus는 구현하지 않는다.
- 알림 발송 로직은 변경하지 않는다.
- target 원문은 노출하지 않는다.
- API 응답에 enabled가 있으면 프론트에서만 필터링한다.
- API 응답에 enabled가 없으면 Backend 변경 필요 여부를 확인하고 최소 범위로 처리한다.
- 완료 후 테스트 방법을 정리한다.
```
