# Due Batch Limit Baseline Test Report

작성일: 2026-07-09

## 1. 목적

`docs/next_feature_request.md`의 due 조회 batch limit 적용 전에 현재 성능을 실측한다.

이번 문서의 범위는 다음 두 가지다.

- 현재 코드 기준 테스트 실행 결과
- PostgreSQL `EXPLAIN (ANALYZE, BUFFERS)` 기준 due 조회 성능 분석

## 2. 이번에 실제 실행한 테스트

### 2.1 전체 테스트

실행 명령:

```powershell
.\gradlew.bat test
```

결과:

- 성공
- `BUILD SUCCESSFUL in 5s`
- `Task :test UP-TO-DATE`

해석:

- H2 기반 일반 테스트는 현재 정상이다.
- batch limit 적용 후에도 이 테스트 집합을 회귀 기준으로 사용하면 된다.

### 2.2 성능 seed 적재

실행 명령:

```powershell
.\gradlew.bat dbSeedTest
```

결과:

- 성공
- `BUILD SUCCESSFUL in 41s`

해석:

- PostgreSQL에 성능 측정용 seed 적재가 완료됐다.
- 이후 `EXPLAIN (ANALYZE, BUFFERS)` 측정은 이 seed 기준으로 해석한다.

## 3. 측정 환경

- DB 컨테이너: `pingbell-postgres`
- PostgreSQL 포트: `5432`
- seed 이후 전체 row 수
  - `monitors`: 10,002
  - `notification_histories`: 100,022

## 4. 현재 데이터 분포

### 4.1 due monitor 분포

실측 결과:

- 전체 monitor: `10,002`
- due monitor: `9,501`
- due 비율: 약 `95.0%`

해석:

- 현재 seed에서는 due monitor 비율이 너무 높다.
- 이 상태에서는 batch limit 효과는 측정 가능하지만, "현실적인 due 비율" 기준 planner 선택을 보기에는 왜곡이 있다.

### 4.2 retry due notification 분포

실측 결과:

- 전체 notification history: `100,022`
- retry due history: `30,000`
- retry due 비율: 약 `30.0%`

해석:

- 이 비율도 실제 운영 기준으로는 높다.
- 정렬 대상이 여전히 크기 때문에 sort 비용과 seq scan 비용이 크게 남는다.

## 5. 현재 쿼리 기준 실측 결과

### 5.1 Scheduler due monitor 현재 쿼리

측정 쿼리:

```sql
select *
from monitors
where status in ('ACTIVE', 'DOWN')
  and deleted_at is null
  and next_check_at <= now();
```

실측 결과:

- Plan: `Seq Scan on monitors`
- rows: `9,501`
- rows removed by filter: `501`
- execution time: `8.265 ms`

해석:

- due 비율이 95%라서 전체 스캔이 선택됐다.
- 현재 구조에서는 batch limit 없이 대상 전체를 메모리로 끌고 간다.

### 5.2 Notification retry due 현재 쿼리

측정 쿼리:

```sql
select *
from notification_histories
where retryable = true
  and next_retry_at <= now()
  and retry_count < max_retry_count
order by next_retry_at asc, id asc;
```

실측 결과:

- Plan: `Seq Scan + external merge sort`
- rows: `30,000`
- rows removed by filter: `70,022`
- temp disk sort: `4,072kB`
- execution time: `76.064 ms`

해석:

- 현재 병목이 가장 명확한 구간이다.
- due 대상 전체를 읽고 정렬까지 수행해서 scheduler batch 처리 비용이 크다.

## 6. batch limit 후보 실측

### 6.1 due monitor 쿼리

측정 쿼리:

```sql
select *
from monitors
where status in ('ACTIVE', 'DOWN')
  and deleted_at is null
  and next_check_at <= now()
order by next_check_at asc, id asc
limit {batchSize};
```

결과 요약:

| batch size | plan | execution time |
| --- | --- | ---: |
| 50 | `idx_monitors_due_active` + Incremental Sort | `0.170 ms` |
| 500 | `idx_monitors_due_active` + Incremental Sort | `0.902 ms` |
| 1000 | `idx_monitors_due_active` + Incremental Sort | `1.182 ms` |

해석:

- `order by + limit`를 넣자마자 현재 있는 `idx_monitors_due_active`를 타기 시작했다.
- 전체 due 비율이 95%여도 batch 크기가 작으면 index scan이 매우 잘 동작한다.
- monitor due 조회는 batch limit 적용 효과가 즉시 크다.

### 6.2 retry due 쿼리

측정 쿼리:

```sql
select *
from notification_histories
where retryable = true
  and next_retry_at <= now()
  and retry_count < max_retry_count
order by next_retry_at asc, id asc
limit {batchSize};
```

결과 요약:

| batch size | plan | execution time |
| --- | --- | ---: |
| 50 | Parallel Seq Scan + Gather Merge + top-N sort | `20.036 ms` |
| 100 | Parallel Seq Scan + Gather Merge + top-N sort | `19.575 ms` |
| 500 | Parallel Seq Scan + Gather Merge + top-N sort | `17.097 ms` |
| 1000 | Parallel Seq Scan + Gather Merge + top-N sort | `22.726 ms` |
| 5000 | Seq Scan + top-N sort | `43.068 ms` |

해석:

- limit를 걸면 `76.064 ms`에서 `17~20 ms` 수준까지 줄어든다.
- 다만 index를 타지 못해서 여전히 전체 스캔은 남아 있다.
- 현재 분포와 현재 인덱스 구조에서는 `500` 근처가 가장 효율적이었다.
- `5000`처럼 커지면 다시 정렬 비용과 스캔 비용이 커져서 이점이 줄어든다.

## 7. 언제 성능이 가장 좋은가

이번 실측 기준으로 정리하면 다음과 같다.

### 7.1 Scheduler due monitor

가장 좋은 조건:

- `order by next_check_at asc, id asc`
- 작은 batch limit
- 현재 seed 기준에서는 `50~500` 구간이 가장 안정적

근거:

- `50`: `0.170 ms`
- `500`: `0.902 ms`
- `1000`: `1.182 ms`

판단:

- monitor due 조회는 batch size를 `100~500` 정도로 잡아도 충분히 빠르다.
- 현재 데이터 분포에서는 이 구간이 처리량과 응답 시간의 균형이 가장 낫다.

### 7.2 Notification retry due

가장 좋은 조건:

- `order by next_retry_at asc, id asc`
- 무제한 조회를 피하고 중간 크기 batch limit 사용

근거:

- no limit: `76.064 ms`
- limit 50: `20.036 ms`
- limit 100: `19.575 ms`
- limit 500: `17.097 ms`
- limit 1000: `22.726 ms`
- limit 5000: `43.068 ms`

판단:

- 현재 seed와 현재 인덱스 기준으로는 `batch size 500`이 가장 좋았다.
- 너무 작아도 전체 스캔 비용은 그대로이고,
  너무 크면 정렬 결과 집합이 커져서 다시 느려진다.

## 8. 현재 기준 결론

### 8.1 바로 적용해도 되는 것

- due monitor 조회 batch limit
- retry due 조회 batch limit
- batch size 설정 분리

이 세 가지는 지금 바로 적용해도 성능 개선 근거가 충분하다.

### 8.2 특히 효과가 큰 구간

- monitor due 조회
  - `8.265 ms` -> `0.170~1.182 ms`
- retry due 조회
  - `76.064 ms` -> `17.097~20.036 ms`

### 8.3 아직 남은 문제

- due monitor 비율 `95%`
- retry due 비율 `30%`

이 분포는 여전히 비현실적이라서, 다음 단계에서는 seed를 1~5% 수준으로 줄인 뒤 다시 `EXPLAIN`을 찍어야 한다.

## 9. 다음 수정 우선순위

1. `MonitorRepository`에 정렬 + limit 기반 due 조회 메서드 추가
2. `NotificationHistoryRepository`에 limit 파라미터 반영
3. batch size를 application 설정으로 분리
4. `CheckService`, `KafkaCheckDispatchService`, `NotificationRetryService` 테스트 보강
5. `DatabasePerformanceSeedTests`에서 due 비율을 1~5%로 조정
6. 조정 후 다시 `dbSeedTest`와 `EXPLAIN (ANALYZE, BUFFERS)` 재측정
