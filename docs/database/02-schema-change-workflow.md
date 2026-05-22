# 스키마 변경 워크플로우

## 기본 흐름
1. 엔티티 변경 필요사항을 먼저 정리한다.
2. `db/migration`에 다음 버전 SQL 파일을 추가한다.
3. 로컬에서 앱 실행으로 migration 적용을 확인한다.
4. `ddl-auto: validate`가 통과하는지 확인한다.
5. PR/배포 시 같은 migration 파일이 동일하게 적용된다.

## 예시
1. `member`에 `nickname` 컬럼 추가가 필요하다.
2. `V2__add_member_nickname.sql` 생성:
```sql
ALTER TABLE member ADD COLUMN nickname VARCHAR(50);
```
3. 앱 실행 후 `flyway_schema_history`에 `V2` 기록 확인.

## 운영 규칙
- 이미 배포된 `V1`, `V2` 파일은 수정하지 않는다.
- 실수 수정은 `V3` 같은 새 파일로 반영한다.
- 개발 DB 초기화가 필요할 때만 `docker compose down -v`를 사용한다.
