# Pingbell RAG 후속 구현 이슈 분해

## 1. 현재 판단

Runbook 규격과 합성 샘플만 준비됐다. 문서 저장소, API, `pgvector`, embedding, 검색, LLM context 연결과 RAG 품질 평가는 모두 미구현이다. 아래 이슈는 한 번에 하나씩 의존 순서대로 진행한다.

공통 원칙:

- 인증 정보에서 계산한 `tenantId`를 모든 문서·chunk·검색 경계에 적용한다.
- AI 실패가 기존 로그 분석, Incident 판정과 알림 흐름을 변경하지 않게 한다.
- 실제 운영 문서, 계정, secret과 개인정보를 fixture에 사용하지 않는다.
- Backend API 계약이 생기거나 변경되면 Frontend 영향 범위를 같은 이슈에서 확인한다.

## 2. 작업 분해와 우선순위

### Issue 1. Runbook metadata 저장·조회 API

- 우선순위: 1
- 주 담당: Backend Agent
- 목적: vector 기능 없이 문서 revision, Tenant 소유권과 상태 전이를 저장·조회할 기반을 만든다.
- 범위: Document/Revision 모델, DTO, 등록·새 version 생성·상세/목록 조회, `DRAFT`에서 `ACTIVE` 활성화, `SUPERSEDED`·`RETIRED` 전이, 인증 기반 Tenant 조건, 민감정보 금지 패턴 검증, Repository/Controller 테스트.
- 제외: Frontend 화면, `pgvector`, embedding, 유사도 검색과 LLM 연결.
- 완료 조건: Entity를 직접 반환하지 않고, 다른 Tenant 직접 조회가 차단되며, 한 계보에 하나의 `ACTIVE`만 존재하고, 금지 패턴 문서가 저장 거부되며, API 계약의 Frontend 영향을 기록한다.
- 테스트: 권한 격리, version 경쟁, 상태 전이, validation과 API 오류 응답 테스트.
- 의존성: 현재 문서 규격.

### Issue 2. PostgreSQL pgvector 확장과 vector schema migration

- 우선순위: 2
- 주 담당: Infra Agent
- 협업: Backend Agent
- 목적: 로컬·테스트 DB에서 명시적으로 vector 저장을 사용할 수 있는 기반만 추가한다.
- 범위: 확장 활성화 방식, Flyway migration, chunk와 embedding metadata schema, Docker Compose 개발 환경, rollback/호환성 문서.
- 제외: embedding 모델 호출, 문서 chunk 생성, 검색 Service와 LLM 연결.
- 완료 조건: migration이 빈 DB와 기존 schema에서 검증되고, 확장 미지원 환경의 실패가 명확하며, secret과 운영 데이터가 init/migration에 없다.
- 테스트: Testcontainers 또는 동등한 PostgreSQL 통합 migration 검증. H2로 vector 동작을 가장하지 않는다.
- 의존성: Issue 1의 문서 식별자와 version 모델 확정.

### Issue 3. Runbook chunking과 embedding 저장 파이프라인

- 우선순위: 3
- 주 담당: AI Agent
- 협업: Backend Agent
- 목적: 활성 Runbook revision을 재현 가능한 chunk로 나누고 교체 가능한 embedding client를 통해 저장한다.
- 범위: 섹션 보존 chunk 규칙, chunk ID, embedding client 추상화, 모델·차원·생성 시각 기록, 활성 version 재색인과 과거 vector 무효화, Fake client 테스트.
- 제외: 사용자 검색 API, 실제 로그 분석 prompt 연결, 외부 모델을 사용하는 기본 CI.
- 완료 조건: 같은 문서 입력의 chunk ID가 안정적이고, 모든 chunk에 `tenantId/documentId/version`이 있으며, secret 없는 합성 샘플만으로 저장 테스트가 통과한다.
- 테스트: chunk 경계, 차원 불일치, 모델 timeout, 재색인과 과거 revision 제외 테스트.
- 의존성: Issue 1, 2.

### Issue 4. Tenant metadata pre-filter와 vector 검색

- 우선순위: 4
- 주 담당: Backend Agent
- 협업: AI Agent
- 목적: Tenant와 활성 최신 revision을 먼저 제한한 뒤 metadata와 vector 점수를 결합해 관련 Runbook을 반환한다.
- 범위: 인증 기반 Tenant filter, `status=ACTIVE`, 최신 version 검증, `serviceName/errorTypes/documentType` filter, top-k와 최소 관련도, 중복 문서 정리, 접근 권한 재검증.
- 제외: LLM prompt 변경, 사용자 화면, 다른 Tenant fallback.
- 완료 조건: 다른 Tenant chunk가 후보·결과·reference에 나타나지 않고, retired/superseded/stale 문서가 제외되며, 관련도 미달이면 빈 결과를 반환한다.
- 테스트: 교차 Tenant 공격, 직접 ID 조회, cache key 격리, 상태·version 경쟁과 낮은 관련도 테스트.
- 의존성: Issue 3.

### Issue 5. 로그 분석 context와 실제 reference 연결

- 우선순위: 5
- 주 담당: AI Agent
- 협업: Backend Agent
- 목적: 검색된 Runbook 중 실제 사용한 내용만 로그 분석 context와 구조화 응답 `references`에 연결한다.
- 범위: prompt version 증가, context 크기 제한, 문서 지시문을 데이터로 취급, reference DTO와 후검증, 응답 조회 시 Tenant 권한 재검증, RAG 실패 시 기존 비-RAG 분석 fallback.
- 제외: 자동 복구, Incident 상태 변경, 사용하지 않은 검색 후보 노출.
- 완료 조건: `documentId/title/version`만 표시되고 실제 context 및 답변 근거와 일치하며, 검색 실패가 기존 분석 흐름을 실패시키지 않는다.
- 테스트: 허위 reference, prompt injection, 권한 회수, retired 전환, 빈 검색과 검색 timeout 테스트.
- 의존성: Issue 4.

### Issue 6. 합성 fixture 기반 RAG 전후 품질 평가

- 우선순위: 6
- 주 담당: AI Agent
- 협업: Backend Agent
- 목적: 기존 4개 로그 fixture에서 비-RAG와 RAG 결과를 같은 기준으로 비교해 근거 연결 개선과 부작용을 측정한다.
- 범위: Runbook 관련성·reference 정확성·Tenant 격리 평가 항목, 동일 모델·설정 반복 평가, 점수와 실패 사유 코드 기록, mock 회귀 테스트.
- 제외: 실제 고객 로그와 응답 원문 저장, 평가 결과 없이 prompt 규칙 추가.
- 완료 조건: 잘못된 reference와 교차 Tenant 노출이 0건이고, 안전성 hard failure가 없으며, 개선되지 않은 항목도 그대로 기록한다.
- 테스트: 기본 CI는 Fake client와 합성 fixture만 사용하고 실제 모델 평가는 API Key 없는 CI와 분리한다.
- 의존성: Issue 5.

## 3. 담당 에이전트

1. Backend: 문서 API와 권한·검색 경계
2. Infra: PostgreSQL 확장과 migration
3. AI: chunk/embedding, context/reference와 품질 평가

Frontend는 Issue 1과 5에서 API 계약 영향만 확인한다. 문서 관리 화면은 별도 요청 전까지 범위에 넣지 않는다.

## 4. 전체 완료 조건

- 각 이슈가 앞선 이슈의 계약을 사용하고 독립적으로 테스트된다.
- Tenant filter가 vector 점수 계산 전부터 적용되고 모든 fallback·cache·reference 경로에서 유지된다.
- 실제 사용한 최신 활성 문서만 `documentId`, `title`, `version`으로 표시된다.
- RAG 실패가 기존 장애 판정이나 로그 분석의 안전한 실패 처리를 훼손하지 않는다.
- 구현하지 않은 단계는 상태, README와 포트폴리오에서 완료로 표현하지 않는다.

## 5. 다음에 사용할 프롬프트

```text
AGENTS.md와 docs/agents/backend-agent.md, docs/agents/ai-agent.md를 읽고 docs/next_feature_request.md의 Runbook metadata 저장·조회 API 이슈만 진행해줘.

조건:
- docs/runbooks/runbook-specification.md를 계약으로 사용한다.
- 인증된 사용자에서 Tenant 경계를 서버가 계산하고 모든 조회에 적용한다.
- 한 documentId에는 하나의 ACTIVE version만 허용한다.
- 민감정보 금지 패턴을 저장 전에 검증한다.
- pgvector, embedding, vector 검색과 RAG 연결은 구현하지 않는다.
- API 계약의 Frontend 영향을 확인한다.
```

