# Runbook chunking과 embedding 저장 규칙

## 범위

이 문서는 활성 Runbook revision을 결정적으로 chunking하고 V14의 pgvector schema에 embedding을 교체 저장하는 내부 규칙을 정의한다. vector 검색, 로그 분석 prompt 연결, 외부 공급자 client 구현과 Frontend 화면은 포함하지 않는다.

## Chunk 경계와 식별자

- `증상`, `영향`, `확인 절차`, `안전한 완화`, `복구 검증`, `재발 방지`, `참고 링크`를 순서대로 각각 독립된 의미 경계로 사용한다.
- 모든 chunk content는 원래 Markdown section heading으로 시작하며 `sectionTitle`에도 같은 의미를 저장한다.
- section 본문은 빈 줄 기준 Markdown block을 우선 보존하며 기본 1,200자 안에서 block 단위로 묶는다.
- 긴 일반 block은 줄과 공백 경계에서 나누고 fenced code block은 Markdown 구조 보존을 위해 원자적으로 유지한다.
- 빈 section도 heading만 포함한 chunk 하나를 생성해 section 의미와 순서를 잃지 않는다.
- CRLF와 LF는 LF로 정규화한다. content hash는 최종 chunk content의 SHA-256 hex다.
- `chunkId`는 `documentId`, `version`, section title, section 내부 part 순서와 content hash를 null 문자로 구분해 SHA-256한 `rbch_<hash>` 형식이다.
- 같은 `documentId/version/본문`은 실행 시각이나 embedding 모델과 무관하게 같은 chunk 순서, hash와 ID를 생성한다.

## Client와 검증

- 공급자 SDK는 `EmbeddingClient` 구현 안에만 둔다. 기본 애플리케이션에는 외부 client가 없음을 명시적으로 실패시키는 구현만 있고, 기본 테스트는 `FakeEmbeddingClient`를 사용한다.
- client 입력과 결과마다 `tenantId/documentId/version/chunkId` 네 경계를 유지한다.
- 저장 전에 model 이름, 선언 차원, 결과 개수, 중복·누락·외부 chunk 경계, 실제 배열 길이와 finite 값을 검증한다.
- timeout과 잘못된 batch는 저장소를 호출하기 전에 실패하므로 기존 활성 embedding을 변경하지 않는다.

## 트랜잭션 교체

1. 외부 embedding 생성과 batch 검증을 DB 교체 전에 끝낸다.
2. 저장 트랜잭션에서 같은 Tenant의 해당 `ACTIVE` revision을 write lock으로 다시 확인한다.
3. chunk를 네 경계로 upsert한다.
4. 해당 revision의 기존 활성 embedding ID를 Tenant·문서·version 경계로 읽고, 각 row를 네 경계로 비활성화한다.
5. 새 vector와 model, 실제 차원, UTC 생성 시각을 네 경계로 저장한다.
6. 차원 CHECK, 활성 unique 제약 또는 insert가 실패하면 비활성화와 부분 insert를 모두 rollback한다.

이 흐름은 Runbook embedding table만 변경하며 Incident, CheckResult, 알림과 로그 분석 흐름에는 연결하지 않는다.
