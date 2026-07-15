# Flyway Migration Test Policy

작성일: 2026-06-27

## 목적

기본 테스트와 PostgreSQL 전용 Flyway migration 검증을 분리한다.

`.\gradlew.bat test`는 Docker/PostgreSQL 없이도 빠르게 통과해야 한다.  
PostgreSQL SQL 문법과 Flyway migration 검증은 별도 명령으로 실행한다.

## 기본 테스트

```powershell
.\gradlew.bat test
```

특징:

- H2 기반 `test` profile 사용
- `migration` 태그 테스트 제외
- Flyway 비활성화
- Hibernate `create-drop`으로 context load용 스키마 생성

## Migration 검증 테스트

```powershell
docker compose up -d postgres
.\gradlew.bat migrationTest
```

특징:

- PostgreSQL 기반 `migration-test` profile 사용
- Flyway 활성화
- JPA `ddl-auto=validate`
- `@Tag("migration")` 테스트만 실행

## 환경변수

기본값은 `.env.example`과 동일한 로컬 Docker Compose 환경을 기준으로 한다.

```text
POSTGRES_DB=pingbell
POSTGRES_USER=pingbell
POSTGRES_PASSWORD=pingbell1234!!
POSTGRES_PORT=5432
```

별도 DB를 사용하려면 다음 값을 지정할 수 있다.

```text
MIGRATION_TEST_DATABASE_URL=jdbc:postgresql://localhost:5432/pingbell
MIGRATION_TEST_DATABASE_USERNAME=pingbell
MIGRATION_TEST_DATABASE_PASSWORD=pingbell1234!!
```

## 주의할 점

- `migrationTest`는 PostgreSQL이 실행 중이지 않으면 실패한다.
- H2는 PostgreSQL partial index 같은 전용 문법을 검증하지 않는다.
- 실제 schema migration 안정성은 `migrationTest`에서 확인한다.
