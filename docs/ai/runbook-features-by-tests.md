# Runbook 기능과 테스트 보장 범위

## 1. 문서 목적

이 문서는 현재 Pingbell의 Runbook 기능을 구현 코드 설명이 아니라 `src/test/java/com/monit/pingbell/runbook`의 테스트 시나리오를 기준으로 정리한다. 테스트가 통과할 때 보장되는 동작과 아직 연결되지 않은 기능을 구분하는 것이 목적이다.

현재 구현 흐름은 다음과 같다.

```text
인증 사용자
  → Runbook 문서와 revision 저장·상태 관리
  → 문서 구조·민감정보 검증
  → ACTIVE revision chunk 분할
  → embedding 생성·교체 저장
  → Tenant·metadata 선필터
  → pgvector 유사도 검색
  → 내부 RunbookSearchResult 반환
```

아직 검색 결과를 로그 분석 prompt나 AI 응답 `references`에 연결하지는 않는다.

## 2. 테스트 구성

| 테스트 클래스 | 테스트 성격 | 주요 검증 대상 |
|---|---|---|
| `RunbookControllerTest` | Spring MVC 통합 테스트 | 인증, API 계약, Tenant 계산, 오류 응답 |
| `RunbookServiceTest` | Spring Boot 서비스 통합 테스트 | 소유권, version, 상태 전이, 동시성 |
| `RunbookContentValidatorTest` | 단위 테스트 | 필수 섹션과 민감정보 차단 |
| `RunbookChunkerTest` | 단위 테스트 | 결정적 chunk ID, 순서, 섹션 보존 |
| `RunbookEmbeddingServiceTest` | 단위 테스트 | embedding client 경계와 저장 전 검증 |
| `RunbookEmbeddingStoreMigrationTest` | PostgreSQL·pgvector 통합 테스트 | embedding 원자적 교체와 rollback |
| `RunbookVectorSearchServiceTest` | 단위 테스트 | 검색 Tenant 재검증과 검색 조건 제한 |
| `RunbookVectorSearchMigrationTest` | PostgreSQL·pgvector 통합 테스트 | 실제 vector 검색과 metadata 선필터 |

`FakeEmbeddingClient`는 외부 embedding 공급자를 호출하지 않고 성공, timeout, 차원 불일치와 부분 응답을 재현하는 테스트 대역이다.

## 3. 문서 API와 인증 경계

현재 제공하는 Runbook API는 다음과 같다.

| Method | Endpoint | 기능 |
|---|---|---|
| `POST` | `/api/runbooks` | 새 Runbook과 version 1 생성 |
| `POST` | `/api/runbooks/{documentId}/versions` | 다음 revision 생성 |
| `GET` | `/api/runbooks` | 인증 Tenant의 ACTIVE Runbook 목록 조회 |
| `GET` | `/api/runbooks/{documentId}/versions/{version}` | 특정 revision 조회 |
| `PATCH` | `/api/runbooks/{documentId}/versions/{version}/status` | 최신 DRAFT 활성화 또는 revision 폐기 |

### `RunbookControllerTest`가 보장하는 내용

#### 인증이 없으면 접근할 수 없다

`requiresAuthentication`은 인증 없이 `GET /api/runbooks`를 호출하면 `403 Forbidden`이 반환되는지 확인한다.

#### Tenant와 owner는 요청자가 입력하지 않는다

`tenantAndOwnerAreDerivedFromAuthentication`은 JWT의 member ID가 서버 내부 `tenantId`와 `ownerId`로 사용되는지 확인한다. 테스트에서는 member ID `7`로 생성했을 때 응답의 두 값이 모두 `7`이다.

즉, 클라이언트가 다른 Tenant ID를 request body에 넣어 문서 소유권을 바꿀 수 있는 계약이 아니다.

#### 직접 ID 접근도 Tenant로 격리한다

`anotherTenantGetsNotFoundForDirectIdAccess`은 Tenant 1이 만든 문서를 Tenant 2가 다음 방식으로 접근할 때 모두 `404 RUNBOOK_NOT_FOUND`가 반환되는지 확인한다.

- 특정 `documentId/version` 조회
- 특정 revision 상태 변경

권한 없음과 존재하지 않음을 같은 응답으로 처리해 다른 Tenant 문서의 존재 여부도 노출하지 않는다.

#### 검증 오류는 공통 오류 계약으로 반환한다

`returnsCommonErrorForSensitiveContent`는 비밀번호가 포함된 문서 등록을 `400 RUNBOOK_SENSITIVE_DATA_DETECTED`로 거부하는지 확인한다.

`validatesDtoAndEnumContract`는 빈 제목, 허용되지 않은 `POSTMORTEM`, 정규화되지 않은 서비스 이름과 빈 오류 유형을 함께 가진 잘못된 DTO가 `400 VALIDATION_ERROR`로 처리되는지 확인한다. 이 테스트는 각 필드를 따로 검증하지는 않지만 복합 invalid request가 Controller 계약을 통과하지 못함을 보장한다. 현재 `documentType`의 허용 값은 `RUNBOOK`뿐이다.

## 4. revision과 상태 관리

Runbook은 내용을 덮어쓰지 않고 같은 `documentId` 아래에 version을 증가시킨 revision을 만든다.

```text
version 1 ACTIVE
  → version 2 ACTIVE 생성
  → version 1 SUPERSEDED
  → version 2 ACTIVE
```

### `RunbookServiceTest`가 보장하는 내용

#### 서비스 조회도 Tenant 조건을 사용한다

`isolatesDirectAccessByTenant`은 Tenant 1의 문서를 Tenant 2가 서비스 메서드로 직접 조회해도 `RUNBOOK_NOT_FOUND`가 발생하는지 확인한다. Controller 보안에만 의존하지 않고 Repository 조회 조건에도 Tenant를 포함한다.

#### 새 ACTIVE version이 이전 ACTIVE를 대체한다

`activatingNewVersionSupersedesPreviousVersionAtomically`은 ACTIVE version 2를 생성하면 version 1이 `SUPERSEDED`가 되고 ACTIVE 목록에는 version 2만 남는지 확인한다.

#### 과거 DRAFT를 다시 최신 문서로 만들 수 없다

`rejectsActivatingAnOlderDraftAndKeepsCurrentActive`은 version 2가 ACTIVE인 상태에서 version 1 DRAFT를 활성화하려는 요청을 거부하고 version 2를 계속 ACTIVE로 유지하는지 확인한다. 활성화할 수 있는 revision은 최신 version뿐이다.

#### RETIRED 문서는 ACTIVE 목록에서 제외한다

`retiredRevisionIsExcludedFromActiveList`는 ACTIVE revision을 `RETIRED`로 전환하면 활성 목록이 비는지 확인한다.

#### 동시 version 생성도 중복되지 않는다

`competingVersionCreationIsSerializedByDocumentLock`은 두 요청이 동시에 새 ACTIVE version을 생성해도 document lock으로 직렬화되어 version 2와 3이 만들어지고 최종 ACTIVE는 version 3 하나만 남는지 확인한다.

상태별 검색 가능 여부는 다음과 같다.

| 상태 | 의미 | 활성 목록·vector 검색 |
|---|---|---|
| `DRAFT` | 작성 중 | 제외 |
| `ACTIVE` | 현재 사용 승인 | 포함 |
| `SUPERSEDED` | 새 version으로 대체됨 | 제외 |
| `RETIRED` | 사용 중단 | 제외 |

## 5. 문서 내용 검증

### `RunbookContentValidatorTest`가 보장하는 내용

#### 합성 Runbook 형식은 저장할 수 있다

`allowsSyntheticRunbook`은 정규화된 metadata와 필수 본문 구조를 가진 합성 문서가 검증을 통과하는지 확인한다.

#### 민감정보와 운영 주소를 차단한다

`rejectsCredentialsAndProductionAddress`는 다음 내용이 포함되면 저장을 거부하는지 확인한다.

- `Authorization: Bearer ...` 같은 인증정보
- `admin.company.com` 같은 운영 환경 주소

비밀번호, API Key, JWT, 내부 주소 등은 Runbook이나 이후 AI context에 저장되기 전에 차단하는 정책이다.

#### 일곱 개 섹션과 순서를 강제한다

`rejectsMissingOrReorderedSections`는 필수 섹션이 없거나 순서가 바뀐 문서를 `RUNBOOK_STRUCTURE_INVALID`로 거부하는지 확인한다.

필수 순서는 다음과 같다.

1. 증상
2. 영향
3. 확인 절차
4. 안전한 완화
5. 복구 검증
6. 재발 방지
7. 참고 링크

## 6. chunk 생성

embedding은 전체 Runbook 하나가 아니라 의미 단위 chunk마다 생성한다.

### `RunbookChunkerTest`가 보장하는 내용

#### 같은 입력은 항상 같은 chunk를 만든다

`sameInputProducesStableBoundariesIdsOrdersAndHashes`는 동일 문서가 LF 또는 CRLF 줄바꿈을 사용해도 다음 값이 같게 생성되는지 확인한다.

- chunk 순서
- 중복 없는 `chunkId`
- SHA-256 `contentHash`
- `tenantId/documentId/version` 경계

따라서 같은 revision을 재색인할 때 chunk 식별자가 임의로 바뀌지 않는다.

#### 긴 섹션은 나누되 의미 제목은 유지한다

`preservesRequiredSectionMeaningAcrossLongAndEmptySections`는 긴 섹션이 여러 chunk로 분할되어도 모든 chunk 본문이 `## 섹션명`으로 시작하는지 확인한다. 검색 결과 chunk 하나만 보더라도 해당 내용이 증상인지 확인 절차인지 알 수 있다.

빈 섹션도 제거하지 않고 heading만 가진 chunk로 보존한다.

## 7. embedding 생성과 저장

### `RunbookEmbeddingServiceTest`가 보장하는 내용

#### 공급자와 독립된 client 경계를 사용한다

`fakeClientStoresEveryEmbeddingWithItsFullBoundary`는 `EmbeddingClient` 결과가 모든 chunk와 일대일로 대응하고 다음 정보를 보존하는지 확인한다.

- `tenantId/documentId/version/chunkId`
- embedding model 이름
- vector 차원
- 생성 시각

실제 공급자 SDK가 서비스 전체로 퍼지지 않고 `EmbeddingClient` 뒤에 격리된다.

#### 외부 client timeout은 기존 embedding을 훼손하지 않는다

`timeoutLeavesExistingActiveEmbeddingsUntouched`는 embedding client가 timeout을 발생시키면 DB 저장을 호출하지 않고 기존 활성 embedding을 유지하는지 확인한다.

#### 잘못된 batch는 저장 전에 거부한다

`dimensionMismatchAndPartialResponseAreRejectedBeforeStorage`는 다음 결과를 `EMBEDDING_BATCH_INVALID`로 거부하고 저장소를 호출하지 않는지 확인한다.

- 선언한 차원과 실제 vector 차원이 다름
- 일부 chunk의 vector가 누락됨

예상하지 않은 boundary와 중복 boundary를 거부하는 구현도 존재하지만, 현재 테스트는 이를 독립 시나리오로 검증하지 않는다.

#### 다른 Tenant revision은 색인할 수 없다

`anotherTenantCannotLoadOrStoreTheRevision`은 요청 Tenant가 소유하지 않은 ACTIVE revision을 조회하거나 embedding으로 저장할 수 없는지 확인한다.

### `RunbookEmbeddingStoreMigrationTest`가 보장하는 내용

이 테스트는 H2가 아니라 실제 `pgvector/pgvector:0.8.2-pg16` Testcontainers에서 실행된다.

#### 재색인은 활성 embedding을 원자적으로 교체한다

`successfulReindexAtomicallyReplacesActiveEmbeddings`는 같은 revision을 다시 색인하면 이전 model의 row는 `active=false`, 새 model의 row만 `active=true`가 되는지 확인한다.

#### 저장 중 실패하면 전체 교체를 rollback한다

`databaseFailureRollsBackDeactivationAndPartialInsert`는 두 번째 vector의 차원을 일부러 틀리게 만들어 DB insert를 실패시킨다. 이때 다음 상태를 확인한다.

- 이전 활성 embedding이 계속 활성
- 잘못된 새 embedding은 한 건도 남지 않음

#### 저장소도 Tenant 소유권을 재확인한다

`anotherTenantCannotReplaceTheActiveRevisionEmbeddings`는 다른 Tenant가 같은 `documentId/version`을 사용해도 기존 embedding을 바꾸거나 외부 Tenant row를 추가할 수 없는지 확인한다.

## 8. pgvector 유사도 검색

검색 순서는 다음과 같다.

```text
query 문자열
  → EmbeddingClient로 query vector 생성
  → Tenant·ACTIVE 최신 version·metadata 후보 제한
  → 같은 model·차원의 vector만 cosine 유사도 계산
  → 최소 관련도 적용
  → 문서별 최고 관련 chunk 선택
  → top-k 제한
  → Tenant 결과 재검증
  → 내부 DTO 반환
```

검색 metadata는 `serviceName`, `errorTypes`, `documentType`이다. 현재 `errorTypes`는 요청 값 중 하나 이상이 revision에 존재하면 후보가 된다.

### `RunbookVectorSearchServiceTest`가 보장하는 내용

#### query embedding부터 Tenant 경계를 유지한다

`queryEmbeddingAndEveryCandidateUseTheAuthenticatedTenant`은 query embedding boundary와 저장소 검색 인자에 인증된 Tenant ID가 전달되는지 확인한다.

#### 저장소가 잘못된 후보를 반환해도 결과에서 다시 제거한다

`resultRevalidationDropsForeignCandidatesInsteadOfFillingTopK`는 검색 저장소 결과에 다른 Tenant 후보가 섞였다고 가정해도 서비스가 이를 제거하는지 확인한다. 제거 후 top-k 수가 부족하더라도 다른 결과로 채우지 않는다.

#### 직접 chunk ID 조회도 Tenant를 반복 적용한다

`directChunkLookupRepeatsTenantBoundaryAndDoesNotFallback`은 직접 `documentId/version/chunkId`를 조회할 때도 인증 Tenant를 사용하며 다른 Tenant fallback을 하지 않는지 확인한다.

#### 검색량과 관련도 입력을 제한한다

`criteriaRejectsUnboundedTopKAndInvalidMinimumRelevance`는 다음 입력을 거부한다.

- `topK`가 최대값 20보다 큼
- 최소 관련도가 0.0 미만 또는 1.0 초과

구현은 `topK < 1`, NaN과 무한대 관련도도 거부하지만, 현재 테스트는 이 세 값을 별도 입력으로 직접 검증하지 않는다.

### `RunbookVectorSearchMigrationTest`가 보장하는 내용

이 테스트도 실제 PostgreSQL pgvector에서 수행된다. fixture에는 정상 문서뿐 아니라 다음 방해 후보를 함께 넣는다.

- 다른 Tenant의 점수가 높은 문서
- 과거 `SUPERSEDED` version
- `DRAFT`, `RETIRED` revision
- 다른 `serviceName`
- 다른 `errorType`
- 같은 문서의 두 번째 chunk

#### vector 계산 전에 후보를 제한한다

`tenantStatusLatestVersionAndMetadataAreFilteredBeforeScoring`은 Tenant 101, `order-api`, `TIMEOUT`, `RUNBOOK` 조건으로 검색했을 때 최신 ACTIVE version의 최고 관련 chunk 하나만 반환되는지 확인한다.

SQL은 `MATERIALIZED` 후보 CTE에서 다음 조건을 먼저 적용하고 이후 CTE에서만 pgvector cosine 거리를 계산한다.

- 인증 `tenantId`
- 활성 embedding
- query와 동일한 model·차원
- `ACTIVE` 최신 revision
- `serviceName`
- `errorTypes`
- `documentType`

통합 fixture는 다른 Tenant, 상태, version, `serviceName`, `errorTypes` 제외와 최종 결과를 검증한다. 다만 모든 fixture의 `documentType`이 `RUNBOOK`이므로 다른 문서 유형 제외는 현재 테스트가 독립적으로 증명하지 못한다. 또한 테스트가 `EXPLAIN` 실행 계획을 assert하지는 않으며, vector 계산 전 필터 순서는 `MATERIALIZED` SQL 구조로 보장한다.

#### 낮은 관련도는 빈 결과로 반환한다

`lowRelevanceReturnsEmptyWithoutCrossTenantOrUnrelatedFill`은 모든 정상 후보가 최소 관련도보다 낮으면 다른 Tenant나 metadata 불일치 문서로 결과 수를 채우지 않고 빈 목록을 반환하는지 확인한다.

#### 직접 식별자도 최신 ACTIVE Tenant 범위만 허용한다

`directIdentifiersRemainTenantAndActiveLatestRevisionScoped`는 다음 조회 결과를 확인한다.

| 조회 | 결과 |
|---|---|
| 소유 Tenant의 최신 ACTIVE chunk | 반환 |
| 다른 Tenant ID로 같은 chunk 조회 | 빈 결과 |
| 과거 SUPERSEDED version 조회 | 빈 결과 |
| RETIRED revision 조회 | 빈 결과 |

## 9. 검색 결과 형식

검색은 Entity를 직접 반환하지 않고 다음 내부 DTO를 사용한다.

```text
RunbookSearchResult
├── documentId
├── title
├── version
├── chunkId
├── sectionTitle
└── content
```

Tenant ID, owner ID, embedding 원문과 유사도 원점수는 결과 DTO에 노출하지 않는다. `chunkId`와 `version`은 이후 실제 prompt에 사용한 근거를 검증하기 위해 유지한다.

## 10. 테스트 실행 방법

### 일반 Runbook 테스트

```powershell
.\gradlew.bat test --tests "com.monit.pingbell.runbook.*"
```

일반 `test` task는 `migration` 태그를 제외하므로 Controller, Service, validator, chunker, embedding/search 단위 테스트를 실행한다.

### 실제 pgvector 통합 테스트

Docker Desktop을 실행한 뒤 다음 명령을 사용한다.

```powershell
.\gradlew.bat migrationTest --tests "com.monit.pingbell.runbook.embedding.RunbookEmbeddingStoreMigrationTest"
.\gradlew.bat migrationTest --tests "com.monit.pingbell.runbook.search.RunbookVectorSearchMigrationTest"
```

Testcontainers가 별도의 테스트용 PostgreSQL·pgvector 컨테이너를 자동 생성하므로 개발용 Compose PostgreSQL을 실행할 필요는 없다.

### 전체 회귀 테스트

```powershell
.\gradlew.bat test
.\gradlew.bat migrationTest
```

## 11. 현재 테스트가 보장하지 않는 범위

다음 기능은 아직 구현 또는 연결되지 않았으므로 현재 Runbook 테스트가 보장하지 않는다.

- 실제 외부 embedding 공급자 호출
- Runbook 재색인을 호출하는 공개 API 또는 scheduler
- vector 검색 REST API와 Frontend 검색 화면
- 검색 결과를 로그 분석 prompt context에 포함하는 기능
- AI 응답 `references` 생성과 후검증
- 검색 cache
- 다른 Tenant 또는 전역 fallback
- RAG 적용 전후 품질 평가
- 자동 복구 또는 Incident 상태 변경

Runbook 검색 실패는 현재 Incident, CheckResult, 알림과 로그 분석 흐름에 연결되어 있지 않으므로 이 기존 기능들의 상태를 변경하지 않는다.
