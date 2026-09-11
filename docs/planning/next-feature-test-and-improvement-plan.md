# Next Feature Test And Improvement Plan

작성일: 2026-07-09

## 1. 현재 판단

- 이번 작업은 MVP 1차 범위 안의 Backend + DB 성능 검증 이슈다.
- 핵심은 인덱스를 추가하는 것이 아니라 due 조회를 한 번에 전부 가져오지 않도록 batch limit을 적용하는 것이다.
- 성능 측정 결과가 의미 있으려면 seed 데이터에서 due 대상 비율이 현실적으로 분포해야 한다.
- 현재 병목 후보는 Scheduler due monitor 조회와 Notification retry due 조회다.

## 2. 작업 분해

1. Scheduler due monitor 조회에 `정렬 + limit`를 적용한다.
2. Notification retry due 조회에 `정렬 + limit`를 적용한다.
3. batch size를 application 설정으로 분리한다.
4. 기존 테스트를 조회 개수 제한과 정렬 안정성 기준으로 수정하거나 추가한다.
5. 성능 테스트용 seed 데이터를 due 조회 검증에 맞는 분포로 조정한다.
6. `EXPLAIN (ANALYZE, BUFFERS)` 결과를 변경 전후로 기록한다.

## 3. 우선순위

1. Repository와 service 또는 dispatch 계층에 batch limit 구조를 먼저 반영한다.
2. 단위 테스트와 JPA 테스트로 limit, filter, order 동작을 고정한다.
3. seed 데이터 분포를 현실적인 수준으로 조정한다.
4. `dbSeedTest` 후 PostgreSQL `EXPLAIN (ANALYZE, BUFFERS)`를 다시 측정한다.
5. 전체 회귀 테스트로 `./gradlew.bat test`를 실행한다.

## 4. 담당 에이전트

- Backend Agent
  - Repository 메서드, service, scheduler 또는 dispatch 계층, 테스트 코드 수정
- Infra Agent
  - PostgreSQL 실행 계획 확인 또는 Docker Compose DB 환경 점검이 필요할 때만 보조

## 5. 각 작업의 완료 조건

- Scheduler due monitor 조회가 설정된 batch size 이하로만 반환된다.
- Notification retry due 조회가 설정된 batch size 이하로만 반환된다.
- 정렬 기준이 `next_check_at asc, id asc`, `next_retry_at asc, id asc`로 명확하게 고정된다.
- 단위 테스트에서 due 대상이 많아도 실제 처리 대상은 batch size까지만 처리됨을 검증한다.
- Repository 또는 JPA 테스트에서 filter, order, limit 동작을 함께 검증한다.
- seed 데이터에서 due monitor 비율과 retry due notification 비율이 각각 전체의 1~5% 수준으로 분포한다.
- `EXPLAIN (ANALYZE, BUFFERS)` 결과에서 조회 row 수 또는 sort 비용 개선 여부를 비교할 수 있다.
- `./gradlew.bat test`가 통과한다.
- schema 변경이 실제로 생기는 경우에만 `./gradlew.bat migrationTest`를 통과한다.

## 6. 테스트 방법

### 6.1 단위 테스트

- `NotificationRetryServiceTest`에서 `findRetryDueHistories(now)` 호출 지점을 batch size 기반 구조에 맞게 검증한다.
- due 대상이 여러 건이어도 repository가 batch size까지만 반환하면 그 개수만 처리되는지 확인한다.
- 실패, 재시도 예정, 최종 실패 케이스가 limit 구조 변경 이후에도 그대로 유지되는지 확인한다.

### 6.2 Scheduler 또는 Dispatch 테스트

- `KafkaCheckDispatchServiceTest` 패턴으로 due monitor 조회 결과가 batch size만큼만 publish되는지 검증한다.
- 같은 `nextCheckAt` 값을 가진 monitor가 여러 개 있을 때 `id asc` 순서로 안정적으로 처리되는지 검증한다.
- direct 모드에서도 같은 기준이 유지되는지 `CheckService` 테스트를 보강한다.

### 6.3 Repository 또는 JPA 테스트

- due 대상과 non-due 대상을 섞어서 저장한 뒤 filter 조건이 정확히 적용되는지 확인한다.
- 정렬 기준이 `next_check_at asc, id asc`, `next_retry_at asc, id asc`인지 검증한다.
- limit이 실제 SQL 레벨에서 반영되어 기대 개수만 반환되는지 검증한다.

### 6.4 Seed 테스트

- `DatabasePerformanceSeedTests`에서 due monitor 비율을 전체의 1~5%로 조정한다.
- retry due notification 비율도 전체의 1~5%로 조정한다.
- due가 아닌 데이터는 미래 시각 또는 retry 불가 상태로 분산한다.
- seed 실행 후 due 비율을 로그 또는 문서에 남겨 explain 결과 해석의 기준으로 삼는다.

### 6.5 성능 테스트

- `./gradlew.bat dbSeedTest`
- PostgreSQL에서 due monitor 조회 쿼리와 notification retry due 조회 쿼리에 대해 `EXPLAIN (ANALYZE, BUFFERS)` 실행
- 비교 항목
  - 실행 시간
  - scan row 수
  - sort 발생 여부
  - planner가 index 또는 seq scan 중 무엇을 선택했는지

### 6.6 전체 회귀 테스트

- `./gradlew.bat test`
- schema 또는 migration 변경이 실제로 생긴 경우에만 `./gradlew.bat migrationTest`

## 7. 구현 시 개선 포인트

- due 조회 메서드는 정렬 기준이 빠지지 않도록 메서드 이름 또는 `Pageable` 구조를 명확히 둔다.
- batch size는 하드코딩하지 말고 설정값으로 관리한다.
- seed 데이터는 현재처럼 due 비율이 과도하게 높아 explain 결과를 왜곡하지 않도록 유지한다.
- 문서에는 단순히 빨라졌다고 쓰지 말고 분포와 실행 계획이 왜 달라졌는지 함께 기록한다.

## 8. 다음에 사용할 프롬프트

```text
너는 Pingbell Backend 에이전트야.
반드시 docs/agents/backend-agent.md 기준을 따라줘.

작업 이슈: [Backend] due 조회 batch limit 적용과 성능 테스트 데이터 분포 개선

이번 작업 범위:
- Monitor due 조회에 batch limit 적용
- Notification retry due 조회에 batch limit 적용
- batch size를 application 설정으로 분리
- 기존 테스트를 limit/정렬 기준에 맞게 수정 또는 추가
- dbSeedTest용 seed 데이터에서 due monitor, retry due notification 비율이 각각 1~5%가 되도록 조정
- EXPLAIN (ANALYZE, BUFFERS) 측정 결과를 문서로 남길 수 있게 쿼리 기준 정리

이번 작업에서 하면 안 되는 것:
- 새로운 인덱스 추가
- Kafka/worker 구조 변경
- API DTO 변경
- Frontend 변경
- 대규모 query rewrite
- migration이 꼭 필요하지 않은데 schema 변경으로 범위를 넓히기

완료 조건:
- due monitor 조회가 batch size 이하로 제한된다
- retry due notification 조회가 batch size 이하로 제한된다
- 정렬 기준이 next_check_at asc, id asc / next_retry_at asc, id asc 로 고정된다
- 관련 테스트가 추가 또는 수정된다
- ./gradlew.bat test 통과
- seed 분포와 EXPLAIN 측정 기준이 문서화된다

결과는 다음 형식으로 정리해줘.
1. 변경 요약
2. 수정 파일
3. 테스트 방법
4. 주의할 점
5. 다음 작업
```
