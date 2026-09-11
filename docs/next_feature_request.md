---
name: Feature Request
about: 다음 개발 작업 요청 기록
title: "[PM] README/포트폴리오 문서에 최근 구현 반영 + 다음 우선순위 재정리"
labels: pm
assignees: ''
---

## 작업 목적

`docs/operations/observability-metrics.md` 8.1절(재시도 관측성 gauge/counter)까지 끝나면서, RAG(Issue 1~6), Frontend(F1~F6 + git 편입 + 필터 2건), 로그 자동 수집, 관측성 기초까지 한 batch의 작업이 마무리됐다(`main`에도 PR #59로 병합 완료). 그런데 `README.md`와 `docs/planning/implementation-priority.md`는 이 작업들을 반영하지 못한 채로 남아 있다 — 이번 세션에서만 "계획 문서가 실제 코드보다 낡아 있는" 경우를 4번 발견했다(관측성 재검토, 알림 채널 필터, monitor UX, slow response 정책). 코드 작업이 아니라 문서/우선순위 정리 작업이다.

## 작업 내용

- [ ] `README.md`를 열어, 현재 구현된 기능 목록(RAG references, 로그 자동 수집, Log Ingestion API Key, 관측성 지표 등)이 반영돼 있는지 확인하고 빠진 부분을 채운다.
- [ ] `docs/planning/implementation-priority.md`를 처음부터 다시 검토한다 — 이번 세션에 이미 손댄 5.1/5.2절뿐 아니라 5.3절(Kafka/Worker/DLQ)도 실제로는 이미 상당 부분 구현되어 있을 가능성이 있다(git 로그에 `feat/28-kafka-consumer-checkresult`, `feat/30-incident-consumer`, `feat/32-notification-consumer`, `feat/34-retry-dlq`, `feat/36~40-dlq-*` 등 병합된 PR이 다수 존재 — "설계 문서만 완료된 범위"라는 문서 설명과 실제 git 이력이 맞는지 코드로 재확인이 필요하다).
- [ ] 확인 결과를 바탕으로 `docs/planning/implementation-priority.md`를 실제 구현 상태에 맞게 다시 쓰고, 진짜 다음 우선순위를 다시 정한다.
- [ ] 남아 있던 작은 후보들(monitor 수정/일시정지/삭제 UX 정리, slow response 표시 정책 점검)도 화면/코드를 직접 열어 이미 됐는지 확인하고 문서에 반영한다.

## 완료 조건

- [ ] README와 implementation-priority.md가 실제 코드 상태(git 이력 포함)와 일치한다.
- [ ] "문서상 미구현"이라고 적힌 항목은 실제로 코드에 없다는 것을 직접 확인한 것이다(git log, 코드 검색으로 재확인 없이 기존 문서 내용을 그대로 믿지 않는다).
- [ ] 다음 우선순위가 근거와 함께 명확히 하나로 좁혀져 있다.

## 제외 범위

- 새 기능 구현(이번 작업은 문서/우선순위 정리만).
- Kafka/Worker/DLQ가 실제로 미구현으로 확인되더라도 이번 이슈에서 바로 구현하지 않는다(다음 이슈로 분리).

## 테스트 기준

- 해당 없음(md 문서만 수정 - AGENTS.md 원칙에 따라 `gradlew test`는 돌리지 않는다).

## 담당 에이전트

- 주 담당: PM Agent

## 다음 작업

- 이 정리 결과에 따라 진짜 다음 코드 작업(Kafka/Worker/DLQ 관련이거나, 여전히 유효한 MVP 안정화 항목)을 새 이슈로 분리한다.

## 다음 에이전트에게 전달할 프롬프트

```text
AGENTS.md와 docs/agents/pm-agent.md를 읽고 docs/next_feature_request.md(README/포트폴리오 문서 반영 + 우선순위 재정리)를 진행해줘.

조건:
- 새 기능은 구현하지 않는다. 문서와 우선순위만 정리한다.
- docs/planning/implementation-priority.md의 "미구현"이라고 적힌 항목은 git log와 코드를 직접 확인해서 재검증한다 - 특히 5.3절(Kafka/Worker/DLQ)이 실제로도 미구현인지 확인할 것(git log에 관련 PR이 이미 여러 개 merge되어 있었다).
- 완료 후 실제로 남은 다음 우선순위 하나를 명확히 제시한다.
```
