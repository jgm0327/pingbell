# Flyway 개념 정리

## 왜 쓰는가
- `docker-entrypoint-initdb.d`는 DB 최초 생성 시 1회만 실행된다.
- 스키마 변경 이력을 코드로 관리하려면 Flyway 같은 마이그레이션 도구가 필요하다.

## 핵심 개념
- Migration 파일: `V{버전}__{설명}.sql` 형식으로 작성한다.
- Schema history: Flyway가 `flyway_schema_history` 테이블로 적용 이력을 관리한다.
- 버전 순서 적용: 아직 적용되지 않은 버전만 순서대로 실행된다.
- 불변 원칙: 이미 배포된 migration 파일은 수정하지 않고, 다음 버전을 추가한다.

## 현재 프로젝트 기준
- 위치: `src/main/resources/db/migration`
- 시작 파일: `V1__create_member_table.sql`
- JPA는 `ddl-auto: validate`로 두고, DDL 생성 책임은 Flyway가 가진다.
