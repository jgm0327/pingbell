# Health Check Scheduler Troubleshooting

개발 중 실제로 발생했던 이슈와 해결 과정을 정리한다.

## 1) Flyway checksum mismatch (V2 변경)

- 증상
  - `FlywayValidateException: Migration checksum mismatch for migration version 2`
- 원인
  - 이미 DB에 적용된 `V2__create_monitors_table.sql` 파일을 수정함.
  - Flyway는 적용 이력 checksum과 로컬 파일 checksum이 다르면 validate 실패.
- 해결
  - `V2`를 원복.
  - 변경사항(`deleted_at`, 인덱스)은 새 마이그레이션 `V4__add_deleted_at_to_monitors.sql`로 분리.
- 재발 방지
  - 적용된 migration 파일은 수정하지 않고 항상 새 버전(`V{n}__...`)으로 추가.

## 2) Scheduler 미동작

- 증상
  - `@Scheduled` 메서드가 호출되지 않음.
- 원인
  - 애플리케이션에 `@EnableScheduling` 누락.
- 해결
  - 메인 애플리케이션 클래스에 `@EnableScheduling` 추가.
- 재발 방지
  - 스케줄러 추가 시 체크리스트에 `@EnableScheduling` 포함.

## 4) `NoClassDefFoundError: org/apache/hc/client5/http/classic/HttpClient`

- 증상
  - 스케줄러 실행 시 런타임 예외 발생.
- 원인
  - `HttpComponentsClientHttpRequestFactory` 사용 중, Apache HttpClient5 의존성이 classpath에 없음.
- 해결
  - `build.gradle`에 의존성 추가:
    - `implementation 'org.apache.httpcomponents.client5:httpclient5'`
- 재발 방지
  - HTTP client factory 교체/추가 시 런타임 의존성 확인.

## 5) `check_results` 스키마-엔티티 불일치

- 증상
  - 컬럼 타입/이름/nullability 불일치로 저장 또는 매핑 오류 가능.
- 원인
  - 초기 SQL에서 `response_time_ms TIMESTAMP`, `check_status` 등 엔티티와 다른 정의 사용.
- 해결
  - 마이그레이션을 엔티티 기준으로 정렬:
  - `status VARCHAR(20)`, `response_time_ms BIGINT`, `http_status` nullable, `created_at/updated_at` 반영.
- 재발 방지
  - 엔티티 변경 시 migration DDL 동시 검토(타입/nullable/컬럼명).

## 6) soft delete 적용 범위 누락

- 증상
  - 스케줄러는 삭제된 모니터를 제외하지만, 일부 조회 API에서 삭제 데이터가 노출될 여지.
- 원인
  - `deleted_at IS NULL` 조건이 조회 메서드마다 일관 적용되지 않음.
- 해결
  - due 체크 조회에 `deletedAtIsNull` 적용 완료.
  - 필요 시 목록/단건 조회 정책도 soft delete 기준으로 통일 예정.
- 재발 방지
  - soft delete 도입 시 “스케줄러/목록/상세/통계” 전 조회 경로 점검.
