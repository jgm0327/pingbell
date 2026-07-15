# pgvector schema migration 운영 가이드

작성일: 2026-07-15

## 적용 범위

Flyway V14는 PostgreSQL에 `vector` extension을 활성화하고 다음 테이블을 추가한다.

- `runbook_chunks`: Runbook revision에서 생성될 chunk 본문과 안정 식별자 저장
- `runbook_chunk_embeddings`: chunk별 vector와 embedding 생성 metadata 저장

이번 단계에는 Entity, Repository, embedding 호출, chunking과 vector 검색을 포함하지 않는다.

## 로컬 PostgreSQL

Docker Compose는 PostgreSQL 16 기반 `pgvector/pgvector:0.8.2-pg16` 이미지를 사용한다.

```powershell
docker compose pull postgres
docker compose up -d postgres
docker compose logs postgres
```

기존 `postgres:16` volume은 같은 PostgreSQL major version을 유지하므로 먼저 DB backup을 만든 뒤 컨테이너를 새 이미지로 재생성한다. `docker compose down -v`는 데이터를 삭제하므로 migration 적용 목적으로 실행하지 않는다.

애플리케이션 시작 시 Flyway가 V14에서 다음 순서로 처리한다.

1. 서버에 pgvector extension 파일이 설치되어 있는지 확인한다.
2. `public` schema에 `vector` extension을 활성화한다.
3. chunk와 embedding table, 제약과 metadata index를 생성한다.

## 식별자와 vector 계약

두 테이블의 모든 row는 다음 경계를 자체 보유한다.

```text
tenant_id
document_id
version
chunk_id
```

- 다른 Runbook table과 DB foreign key를 만들지 않는다.
- 이후 저장 파이프라인은 네 값을 한 묶음으로 복사하고 조회해야 한다.
- `runbook_chunks`는 경계 안의 `chunk_id`와 `chunk_order` 중복을 거부한다.
- embedding row는 `model_name`, `embedding_dimension`, `generated_at`, `active`를 기록한다.
- 같은 경계의 chunk에는 활성 embedding 하나만 허용한다.
- `embedding_dimension`과 실제 `vector_dims(embedding)`이 다르면 `ck_runbook_chunk_embedding_dimension` 제약으로 실패한다.
- vector column은 PostgreSQL pgvector의 실제 `vector` 타입이다. 모델별 차원은 row metadata와 CHECK 제약으로 검증한다.

## 확장 미지원과 권한 오류

pgvector가 서버에 설치되지 않았으면 V14는 다음 식별 메시지와 SQLSTATE `0A000`으로 중단된다.

```text
PGVECTOR_EXTENSION_NOT_AVAILABLE
```

복구 순서:

1. PostgreSQL major version에 맞는 pgvector 패키지 또는 pgvector Docker 이미지를 설치한다.
2. Flyway 접속 계정이 `CREATE EXTENSION vector`를 수행할 권한이 있는지 확인한다.
3. `vector` extension이 `public` schema에 설치 가능한지 확인한다.
4. 실패한 application 배포를 그대로 재실행해 Flyway V14를 다시 적용한다.

Managed PostgreSQL은 provider가 pgvector를 지원하고 extension 활성화 권한을 제공하는지 배포 전에 확인한다. 이미 다른 schema에 설치된 `vector` extension은 V14의 `public.vector` 계약과 호환되지 않으므로 DBA가 위치를 정리한 뒤 적용한다.

## rollback과 호환성

- 배포된 V14 파일을 수정하거나 Flyway history를 수동 repair하지 않는다.
- V14는 신규 table만 추가하므로 애플리케이션 rollback 시 기존 V13 API는 새 table을 무시하고 동작할 수 있다.
- 즉시 rollback이 필요하면 먼저 애플리케이션을 이전 버전으로 되돌리고 V14 table은 유지한다.
- table과 extension 제거가 반드시 필요하면 backup과 데이터 보존 여부를 확인한 별도 forward migration으로 처리한다. `DROP EXTENSION vector CASCADE`는 vector table을 함께 삭제할 수 있어 수동 복구 절차로 사용하지 않는다.
- PostgreSQL major version 변경은 이 migration과 분리한다.

## 테스트

```powershell
.\gradlew.bat test
.\gradlew.bat migrationTest
```

`migrationTest`는 Testcontainers로 다음을 검증한다.

- PostgreSQL 16 + pgvector 0.8.2 빈 DB의 V1~V14 전체 migration과 Hibernate validation
- V13까지만 적용한 별도 DB의 V14 증분 migration
- 실제 vector column과 extension version
- vector 차원 불일치와 chunk 식별자 중복 실패
- 순정 `postgres:16`에서 확장 미지원 메시지 확인

fixture에는 합성 tenant/document/model 이름과 임의 vector만 사용한다.
