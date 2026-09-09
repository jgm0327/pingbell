---
name: Feature Request
about: 다음 개발 작업 요청 기록
title: "[AI] RAG 합성 fixture 기반 전후 품질 평가"
labels: ai
assignees: ''
---

## 작업 목적

`docs/ai/rag-implementation-issues.md`의 Issue 6을 진행한다. Issue 1~5(문서 API, pgvector migration, chunking/embedding 파이프라인, tenant pre-filter 벡터 검색, 로그 분석 context/reference 연결)는 이미 완료된 상태이고, RAG 전체 파이프라인이 실제로 로그 분석 품질을 개선하는지, 부작용(잘못된 reference·교차 Tenant 노출)은 없는지 측정하는 마지막 단계만 남아 있다. Frontend F1~F6(`docs/planning/frontend-implementation-issues.md`)은 이번에 전부 끝났다.

## 작업 내용

- [ ] 기존 4개 로그 fixture(합성 데이터, 실제 고객 로그 아님)에 대해 비-RAG(Runbook context 없음)와 RAG(Runbook context 있음) 분석 결과를 같은 모델/설정으로 반복 실행해서 비교한다.
- [ ] 평가 항목: Runbook 관련성, reference 정확성(모델이 인용한 chunkId가 실제 제공된 context와 일치하는지), Tenant 격리(다른 Tenant의 chunk가 후보/결과/reference 어디에도 안 나타나는지).
- [ ] 항목별 점수와 실패 사유 코드를 기록한다. 개선되지 않은 항목이 있어도 그대로 기록한다(감추지 않는다).
- [ ] mock 기반 회귀 테스트를 추가한다(Fake LLM/embedding client + 합성 fixture만 사용).

## 완료 조건

- [ ] 평가 결과에서 잘못된 reference와 교차 Tenant 노출이 0건이다.
- [ ] 안전성 관련 hard failure(예: 위험한 명령어 노출, 개인정보 노출)가 없다.
- [ ] 개선되지 않은 항목도 있는 그대로 기록되어 있다(과장 없음).
- [ ] 기본 CI는 Fake client와 합성 fixture만 쓰고, 실제 모델을 호출하는 평가는 API Key 없는 CI와 분리되어 있다.

## 제외 범위

- 실제 고객 로그 원문이나 실제 모델 응답 원문 저장
- 평가 결과 없이 prompt 규칙을 먼저 바꾸는 것
- Runbook 문서 관리 화면, RAG 관련 신규 Frontend 화면(이번 요청 범위 아님 — F5에서 이미 references 표시는 끝남)

## 테스트 기준

- 기본 CI: Fake client + 합성 fixture로 동일 입력 → 동일(또는 결정적) 평가 결과 재현
- 별도 트랙: 실제 모델(API Key 필요)로 4개 fixture 각각 비-RAG/RAG 결과를 뽑아 항목별 점수·실패 사유를 문서(`docs/ai/` 하위)로 남긴다

## 담당 에이전트

- 주 담당: AI Agent
- 협업: Backend Agent

## 다음 작업

- 이 평가 결과에 따라 RAG 품질이 기준 이하인 항목이 있으면 chunking 규칙이나 prompt를 조정하는 후속 작업이 생길 수 있다(평가 결과가 나오기 전까지는 범위를 정하지 않는다).

## 다음 에이전트에게 전달할 프롬프트

```text
AGENTS.md와 docs/agents/ai-agent.md, docs/agents/backend-agent.md를 읽고 docs/ai/rag-implementation-issues.md의 Issue 6(합성 fixture 기반 RAG 전후 품질 평가)만 진행해줘.

조건:
- 실제 고객 로그나 실제 모델 응답 원문은 저장하지 않는다 - 합성 fixture만 쓴다.
- Runbook 관련성/reference 정확성/Tenant 격리 세 항목을 평가하고, 개선 안 된 항목도 그대로 기록한다.
- 기본 CI는 Fake client로만 돌고, 실제 모델 평가는 API Key 없는 CI와 분리한다.
- 평가 결과 없이 prompt 규칙을 먼저 바꾸지 않는다.
```
