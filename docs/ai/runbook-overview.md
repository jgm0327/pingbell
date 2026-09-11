# Pingbell Runbook 이해하기

## 1. Runbook이란 무엇인가

Runbook은 장애가 발생했을 때 운영자가 확인하고 따라갈 수 있는 **장애 대응 절차서**다.

예를 들어 `order-api`에서 timeout이 반복된다면 Runbook에는 다음 내용이 들어간다.

- 어떤 증상이 나타나는가
- 사용자와 다른 서비스에 어떤 영향이 있을 수 있는가
- 무엇부터 확인해야 하는가
- 즉시 적용할 수 있는 안전한 완화 방법은 무엇인가
- 정상 복구 여부를 어떻게 검증하는가
- 같은 장애의 재발을 어떻게 줄일 것인가

즉, Runbook은 프로그램이 자동으로 서버를 고치는 스크립트가 아니다. 사람이 안전하게 판단하고 대응하도록 돕는 문서이며, Pingbell에서는 향후 AI 장애 분석이 참고할 운영 근거로도 사용한다.

## 2. Pingbell에서 Runbook을 사용하는 이유

Pingbell은 URL health check를 통해 timeout, HTTP 5xx, 응답 지연 같은 장애를 발견할 수 있다. 하지만 장애를 발견하는 것과 원인을 파악하고 해결하는 것은 다른 문제다.

```text
Health check가 알려주는 것
  → order-api가 timeout 상태다.

운영자가 추가로 알고 싶은 것
  → 어디부터 확인해야 하는가?
  → 이전에 같은 장애를 어떻게 처리했는가?
  → 어떤 조치는 안전하고 어떤 조치는 승인이 필요한가?
  → 정상 복구를 어떻게 확인하는가?
```

Runbook이 없으면 AI는 일반적인 지식만으로 답하거나 근거 없는 해결책을 제안할 가능성이 있다. Runbook을 사용하면 서비스 운영자가 미리 검토한 절차를 검색해 더 구체적인 확인 순서와 근거를 제공할 수 있다.

Pingbell에서 Runbook을 사용하는 주요 목적은 다음과 같다.

1. 장애 대응 절차를 사람마다 다르게 기억하지 않고 문서화한다.
2. 서비스별 확인 순서와 안전한 완화 방법을 일관되게 유지한다.
3. 오래되거나 폐기된 절차 대신 최신 승인 version만 사용한다.
4. 다른 Tenant의 내부 운영 문서가 섞이지 않도록 격리한다.
5. 향후 AI 분석이 일반적인 추측보다 실제 운영 문서를 근거로 답하게 한다.

## 3. Runbook 문서에는 무엇이 들어가는가

Pingbell Runbook은 metadata와 본문으로 구성된다.

### Metadata

| 필드 | 의미 |
|---|---|
| `documentId` | 같은 Runbook 계보를 식별하는 ID |
| `tenantId` | 문서를 소유하고 검색할 수 있는 Tenant 경계 |
| `ownerId` | 문서 검토와 관리 책임자 |
| `title` | Runbook 제목 |
| `documentType` | 현재는 `RUNBOOK`만 허용 |
| `serviceName` | 문서가 적용되는 서비스 이름 |
| `errorTypes` | `TIMEOUT`, `HTTP_5XX` 같은 관련 오류 유형 |
| `version` | 변경 이력을 보존하는 revision 번호 |
| `status` | `DRAFT`, `ACTIVE`, `SUPERSEDED`, `RETIRED` |

### 본문

본문은 다음 일곱 섹션을 순서대로 사용한다.

1. `증상`
2. `영향`
3. `확인 절차`
4. `안전한 완화`
5. `복구 검증`
6. `재발 방지`
7. `참고 링크`

이 구조를 고정하면 사람이 문서를 읽기 쉽고, 긴 문서를 여러 chunk로 나눠 검색해도 각 내용의 의미를 유지할 수 있다.

## 4. Document, Revision, Chunk, Embedding의 차이

Runbook 기능에는 비슷해 보이는 네 가지 개념이 있다.

```text
Runbook Document
└── Revision version 1
└── Revision version 2
    ├── Chunk: 증상
    ├── Chunk: 영향
    ├── Chunk: 확인 절차
    └── ...
         └── 각 Chunk의 Embedding vector
```

### Document

`documentId`로 구분되는 Runbook의 계보다. 내용이 바뀌어도 같은 목적의 Runbook이면 같은 Document에 속한다.

### Revision

특정 시점의 변경 불가능한 문서 version이다. 기존 내용을 덮어쓰지 않고 version 1, 2, 3처럼 새 revision을 추가한다.

### Chunk

긴 Runbook을 검색 가능한 작은 의미 단위로 나눈 것이다. Pingbell은 일곱 개 필수 섹션을 기본 경계로 사용한다. 긴 섹션은 더 나눌 수 있지만 각 chunk에는 섹션 제목을 반복해 의미를 보존한다.

### Embedding

chunk 내용을 숫자 배열로 변환한 값이다. 사용자의 장애 설명도 같은 방식으로 숫자 배열로 바꾸면 pgvector가 의미상 가까운 Runbook chunk를 찾을 수 있다.

Embedding은 문서 원문을 대체하지 않는다. 검색 후보를 찾기 위한 숫자 표현이며, 실제 결과에는 원본 chunk의 제목과 내용이 사용된다.

## 5. Runbook 작성과 상태 전이 흐름

새 Runbook은 다음 흐름으로 관리한다.

```text
1. 인증 사용자가 Runbook 작성
2. 서버가 인증 정보로 tenantId와 ownerId 결정
3. metadata와 본문 구조 검증
4. 민감정보와 운영 주소 검사
5. DRAFT 또는 ACTIVE revision 저장
6. 검토가 끝난 최신 DRAFT를 ACTIVE로 전환
7. 새 ACTIVE version이 생기면 이전 ACTIVE는 SUPERSEDED
8. 더 이상 안전하지 않은 문서는 RETIRED
```

상태의 의미는 다음과 같다.

| 상태 | 의미 | 검색 대상 |
|---|---|---|
| `DRAFT` | 작성 또는 검토 중 | 아니요 |
| `ACTIVE` | 현재 사용하도록 승인된 최신 절차 | 예 |
| `SUPERSEDED` | 새 version으로 대체된 과거 절차 | 아니요 |
| `RETIRED` | 더 이상 사용하면 안 되는 절차 | 아니요 |

같은 Document에는 ACTIVE revision이 하나만 존재한다. 과거 DRAFT를 활성화해 현재 절차를 이전 내용으로 되돌리는 것도 허용하지 않는다.

## 6. Chunk와 Embedding 생성 흐름

ACTIVE Runbook을 vector 검색에 사용할 수 있도록 만드는 과정은 다음과 같다.

```text
ACTIVE revision 확인
  → 필수 섹션 기준으로 chunk 분할
  → 각 chunk에 tenantId/documentId/version/chunkId 부여
  → EmbeddingClient로 chunk vector 생성
  → 결과 개수·경계·차원 검증
  → pgvector 테이블에 원자적으로 교체 저장
```

중요한 동작은 다음과 같다.

- 같은 문서와 본문은 줄바꿈 형식이 달라도 같은 chunk ID를 만든다.
- embedding 공급자가 timeout을 일으키면 기존 활성 embedding을 유지한다.
- 일부 vector가 빠지거나 차원이 다르면 저장 전에 거부한다.
- 저장 도중 실패하면 이전 embedding 비활성화와 새 embedding 일부 저장을 모두 rollback한다.
- 다른 Tenant는 같은 `documentId`를 알고 있어도 해당 문서를 색인할 수 없다.

## 7. 장애 상황에서 Runbook을 검색하는 흐름

예를 들어 다음 장애 정보가 있다고 가정한다.

```text
serviceName: order-api
errorTypes: [TIMEOUT]
query: upstream 요청이 5초 후 반복적으로 timeout 된다
```

현재 내부 vector 검색은 다음 순서로 동작한다.

```text
1. query를 EmbeddingClient로 vector 변환
2. 인증된 tenantId의 문서만 후보로 제한
3. ACTIVE 최신 version만 후보로 제한
4. serviceName, errorTypes, documentType 적용
5. 같은 embedding model과 차원만 비교
6. pgvector cosine 유사도 계산
7. 최소 관련도 미달 후보 제거
8. 같은 문서에서는 가장 관련도 높은 chunk 선택
9. top-k 상한 적용
10. 결과의 Tenant 경계를 다시 검증
11. 내부 RunbookSearchResult 반환
```

필터를 vector 계산 전에 적용하는 이유는 보안과 비용 때문이다. 다른 Tenant 문서가 점수 계산 후보에 들어오지 않으며, 관련 없는 문서의 vector를 불필요하게 비교하지 않는다.

기준을 통과한 문서가 없으면 결과는 빈 목록이다. top-k 개수를 채우기 위해 다른 Tenant나 관련 없는 문서를 가져오는 fallback은 사용하지 않는다.

## 8. 검색 결과는 무엇을 반환하는가

검색 결과는 JPA Entity나 embedding vector를 직접 반환하지 않는다.

```text
RunbookSearchResult
├── documentId
├── title
├── version
├── chunkId
├── sectionTitle
└── content
```

`version`과 `chunkId`를 유지하는 이유는 나중에 AI가 실제로 사용한 근거가 어떤 문서의 어느 부분인지 검증하기 위해서다.

`tenantId`, `ownerId`, embedding 원문과 유사도 원점수는 검색 결과 DTO에 노출하지 않는다.

## 9. 전체 동작 예시

### 1단계: 운영자가 Runbook을 작성한다

```text
제목: order-api upstream timeout 대응
서비스: order-api
오류 유형: TIMEOUT
상태: ACTIVE
```

본문에는 timeout 증상, 확인할 지표, 안전한 완화 방법과 복구 검증 방법을 작성한다.

### 2단계: 문서를 검색 가능하게 만든다

Runbook을 섹션별 chunk로 나누고 각 chunk의 embedding을 pgvector에 저장한다.

### 3단계: Pingbell이 timeout 장애를 감지한다

기존 health check와 Incident 로직이 장애를 판정한다. Runbook이나 AI가 장애 발생 여부를 결정하지 않는다.

### 4단계: 관련 Runbook을 찾는다

인증 Tenant, `order-api`, `TIMEOUT` 조건을 먼저 적용하고 장애 설명과 의미가 가까운 chunk를 찾는다.

### 5단계: 운영자가 대응 근거로 사용한다

현재는 내부 검색 결과까지만 구현되어 있다. 다음 단계에서는 검색된 chunk 중 실제 사용한 내용만 AI 로그 분석 context에 넣고 검증된 문서만 `references`로 표시할 예정이다.

## 10. Incident 및 AI 분석과의 관계

Runbook은 장애 판정 기능이 아니다.

```text
Health check와 Incident
  → 장애 발생·복구 여부 결정

Runbook 검색
  → 관련 운영 절차 탐색

AI 로그 분석
  → 장애 상황 설명과 확인 방향 제안
```

Runbook 검색이나 embedding 생성이 실패해도 다음 기존 흐름은 영향을 받지 않아야 한다.

- CheckResult 저장
- Incident 생성과 복구
- 장애 알림
- NotificationHistory 저장

AI도 Runbook 내용을 참고해 설명할 뿐 자동으로 서버를 재시작하거나 Incident 상태를 변경하지 않는다.

## 11. Tenant 경계가 중요한 이유

Runbook에는 서비스 구조와 장애 대응 절차 같은 내부 운영 정보가 들어갈 수 있다. 따라서 문서 ID를 숨기는 것만으로는 충분하지 않다.

Pingbell은 다음 모든 단계에 인증된 Tenant ID를 반복 적용한다.

- 문서 생성과 조회
- revision 상태 변경
- chunk 생성과 embedding 저장
- vector 검색 후보 제한
- 검색 결과 재검증
- 직접 `documentId/version/chunkId` 조회

검색 결과가 부족해도 다른 Tenant 문서를 가져오는 global fallback은 없다. cache가 나중에 추가되더라도 cache key와 cache 결과 재조회에 Tenant 경계를 포함해야 한다.

## 12. 현재 구현된 범위

| 기능 | 현재 상태 |
|---|---|
| Runbook 생성·새 version·조회·상태 변경 API | 구현됨 |
| 인증 기반 Tenant 격리 | 구현됨 |
| 문서 구조·민감정보 검증 | 구현됨 |
| 섹션 기반 chunking | 구현됨 |
| embedding client 추상화 | 구현됨 |
| chunk와 embedding pgvector 저장 | 구현됨 |
| Tenant·metadata 기반 vector 검색 | 내부 서비스로 구현됨 |
| 실제 외부 embedding 공급자 | 미구현 |
| 재색인 공개 API 또는 scheduler | 미구현 |
| vector 검색 REST API | 미구현 |
| 검색 Frontend 화면 | 미구현 |
| 로그 분석 prompt에 Runbook 연결 | 미구현 |
| AI 응답 `references` 표시 | 미구현 |
| 자동 복구 | 제외 범위 |

따라서 현재 단계는 **Runbook을 안전하게 저장하고 vector로 검색할 수 있는 Backend 내부 기반이 완성된 상태**다. 아직 사용자가 화면에서 검색하거나 AI 분석 결과에서 Runbook 출처를 보는 단계는 아니다.

## 13. 관련 문서

- `docs/runbooks/runbook-specification.md`: 문서 작성 규격과 보안 정책
- `docs/ai/runbook-chunking-and-embedding.md`: chunk와 embedding 저장 세부 규칙
- `docs/ai/runbook-features-by-tests.md`: 테스트 코드가 보장하는 기능 범위
- `docs/ai/rag-implementation-issues.md`: 전체 RAG 구현 순서와 후속 이슈
- `docs/database/03-pgvector-schema-migration.md`: pgvector schema와 migration 운영 방법
