# Flyway Migration Test Policy

작성일: 2026-07-15

## 목적

기본 테스트와 PostgreSQL 전용 Flyway migration 검증을 분리한다.

`.\gradlew.bat test`는 Docker/PostgreSQL 없이도 빠르게 통과해야 한다.  
PostgreSQL SQL 문법과 Flyway migration 검증은 Testcontainers 기반 별도 명령으로 실행한다.

## 기본 테스트

```powershell
.\gradlew.bat test
```

특징:

- H2 기반 `test` profile 사용
- `migration` 태그 테스트 제외
- Flyway 비활성화
- Hibernate `create-drop`으로 context load용 스키마 생성
- H2에 vector alias를 추가하지 않음

## Migration 검증 테스트

```powershell
.\gradlew.bat migrationTest
```

특징:

- Docker가 실행 중인 환경에서 PostgreSQL 16 Testcontainers 사용
- pgvector migration은 `pgvector/pgvector:0.8.2-pg16` 실제 이미지 사용
- Flyway 활성화와 JPA `ddl-auto=validate`
- `@Tag("migration")` 테스트만 실행
- 빈 DB 전체 migration과 V13 schema 증분 migration 검증
- 순정 `postgres:16`에서 pgvector 미지원 실패 검증
- 실제 vector 차원과 식별자 unique 제약 검증

## 주의할 점

- `migrationTest`는 Docker daemon을 사용할 수 없으면 실패한다.
- 첫 실행은 PostgreSQL, pgvector와 Testcontainers 이미지를 내려받으므로 시간이 더 걸릴 수 있다.
- H2는 PostgreSQL partial index와 pgvector 타입을 검증하지 않는다.
- 실제 schema migration 안정성은 `migrationTest`에서 확인한다.
- migration fixture에는 실제 운영 secret, 고객 문서와 개인정보를 사용하지 않는다.
