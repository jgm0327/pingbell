# CheckResult Index Performance After Skewed Seed

작성일: 2026-07-08

## 목적

기존 DB 성능 측정에서는 `check_results`가 monitor별로 거의 10건씩 균등 분산되어 있어 실제 성능 저하를 보기 어려웠다.
이번 측정은 hot monitor에 `check_results`가 집중되는 분포로 seed 데이터를 바꾼 뒤, CheckResult 조회 쿼리에 대한 복합 인덱스 효과를 다시 확인한 결과다.

## Seed 데이터 분포

기본 seed 설정:

- members: 100
- monitors: 10,000
- check_results: 100,000
- hot monitor count: 20
- hot check result percent: 70%

분포 확인 결과:

| 항목 | 값 |
| --- | ---: |
| check result가 있는 monitor 수 | 9,510 |
| monitor별 최소 check result 수 | 1 |
| monitor별 최대 check result 수 | 3,620 |
| monitor별 평균 check result 수 | 10.52 |

상위 hot monitor:

| monitor_id | check_results |
| ---: | ---: |
| 40013 | 3,620 |
| 40018 | 3,608 |
| 40010 | 3,591 |
| 40009 | 3,562 |
| 40014 | 3,546 |

이번 측정의 대표 monitor는 `monitor_id = 40013`이다.

## 측정 전 기본 인덱스

`check_results`에는 Flyway로 생성된 기존 인덱스만 있는 상태에서 baseline을 측정했다.

```text
check_results_pkey
idx_check_results_created_at
idx_check_results_monitor_id
```

## 테스트한 후보 인덱스

```sql
create index idx_check_results_monitor_id_id_desc
on check_results(monitor_id, id desc);

create index idx_check_results_monitor_created_at_desc
on check_results(monitor_id, created_at desc);
```

## 결과 요약

| 쿼리 | Before plan | Before time | After plan | After time | 판단 |
| --- | --- | ---: | --- | ---: | --- |
| 목록 pagination | PK backward scan + filter | 11.230 ms | `idx_check_results_monitor_id_id_desc` | 0.086 ms | 추가 추천 |
| 최신 1건 | `idx_check_results_created_at` + filter | 0.122 ms | `idx_check_results_monitor_created_at_desc` | 0.051 ms | 추가 가능 |
| 24시간 trend | `idx_check_results_monitor_id` | 2.733 ms | `idx_check_results_monitor_id` | 2.844 ms | 개선 없음 |
| 1시간 trend | `idx_check_results_monitor_id` + filter | 1.151 ms | `idx_check_results_monitor_created_at_desc` index only scan | 0.213 ms | 추가 추천 가능 |

## 상세 결과

### 1. CheckResult 목록 pagination

테스트 쿼리:

```sql
select *
from check_results
where monitor_id = 40013
order by id desc
limit 20;
```

Before:

```text
Limit
  -> Index Scan Backward using check_results_pkey on check_results
     Filter: monitor_id = 40013
     Rows Removed by Filter: 64960
Execution Time: 11.230 ms
```

After:

```text
Limit
  -> Index Scan using idx_check_results_monitor_id_id_desc on check_results
     Index Cond: monitor_id = 40013
Execution Time: 0.086 ms
```

판단:

`order by id desc limit 20` 쿼리에서 기존에는 PK 역방향 scan을 하면서 `monitor_id`가 맞지 않는 row 64,960건을 버렸다.
복합 인덱스 추가 후에는 특정 monitor의 최신 row만 바로 읽으므로 실행 시간이 `11.230 ms`에서 `0.086 ms`로 줄었다.

이 인덱스는 추가 가치가 명확하다.

```sql
create index idx_check_results_monitor_id_id_desc
on check_results(monitor_id, id desc);
```

### 2. CheckResult 최신 1건

테스트 쿼리:

```sql
select *
from check_results
where monitor_id = 40013
order by created_at desc
limit 1;
```

Before:

```text
Limit
  -> Index Scan Backward using idx_check_results_created_at on check_results
     Filter: monitor_id = 40013
     Rows Removed by Filter: 8
Execution Time: 0.122 ms
```

After:

```text
Limit
  -> Index Scan using idx_check_results_monitor_created_at_desc on check_results
     Index Cond: monitor_id = 40013
Execution Time: 0.051 ms
```

판단:

새 복합 인덱스가 사용되며 실행 시간은 줄었다.
다만 기존 `created_at` 단일 인덱스도 최신 row 근처에서 운 좋게 대상 monitor를 찾으면 충분히 빠를 수 있다.
최신 1건만 보면 필수라고 보긴 어렵지만, 시간 범위 trend 조회까지 함께 보면 추가 가치가 있다.

```sql
create index idx_check_results_monitor_created_at_desc
on check_results(monitor_id, created_at desc);
```

### 3. CheckResult 24시간 trend

테스트 쿼리:

```sql
select date_trunc('hour', cr.created_at) as bucket,
       sum(case when cr.status = 'SUCCESS' then 1 else 0 end) as success_count,
       sum(case when cr.status <> 'SUCCESS' then 1 else 0 end) as failure_count,
       avg(cr.response_time_ms) as avg_response_time
from check_results cr
where cr.monitor_id = 40013
  and cr.created_at >= now() - interval '24 hours'
  and cr.created_at < now()
group by bucket
order by bucket;
```

Before:

```text
Index Scan using idx_check_results_monitor_id
Rows: 3620
Execution Time: 2.733 ms
```

After:

```text
Index Scan using idx_check_results_monitor_id
Rows: 3620
Execution Time: 2.844 ms
```

판단:

24시간 조건에서는 hot monitor의 check result 대부분이 조회 범위에 포함된다.
따라서 `created_at` 조건의 선택도가 낮고, PostgreSQL은 기존 `idx_check_results_monitor_id`를 계속 선택했다.
이 쿼리만 놓고 보면 새 인덱스 효과는 없다.

### 4. CheckResult 1시간 trend

시간 범위가 좁을 때 `created_at` 복합 인덱스가 의미 있는지 확인하기 위해 1시간 범위도 추가 측정했다.

테스트 쿼리:

```sql
select date_trunc('hour', cr.created_at) as bucket,
       count(*)
from check_results cr
where cr.monitor_id = 40013
  and cr.created_at >= now() - interval '1 hour'
  and cr.created_at < now()
group by bucket
order by bucket;
```

Before:

```text
Index Scan using idx_check_results_monitor_id
Filter: created_at >= now() - interval '1 hour'
Rows Removed by Filter: 3439
Execution Time: 1.151 ms
```

After:

```text
Index Only Scan using idx_check_results_monitor_created_at_desc
Index Cond: monitor_id = 40013, created_at range
Heap Fetches: 0
Execution Time: 0.213 ms
```

판단:

시간 범위가 좁아지면 `(monitor_id, created_at desc)` 인덱스가 확실히 효과를 낸다.
기존에는 monitor의 전체 row를 읽고 시간 조건으로 대부분을 버렸지만, 복합 인덱스 추가 후에는 시간 범위를 인덱스 조건으로 직접 처리한다.

## 최종 판단

현재 skewed seed 데이터 기준으로 다음 인덱스는 Flyway migration 추가를 검토할 가치가 있다.

```sql
create index idx_check_results_monitor_id_id_desc
on check_results(monitor_id, id desc);
```

근거:

- CheckResult 목록 pagination에서 실행 시간이 `11.230 ms`에서 `0.086 ms`로 개선됐다.
- 기존 PK backward scan이 불필요하게 많은 row를 필터링하던 문제를 해결한다.
- API 목록 조회와 최신순 pagination에 직접적인 효과가 있다.

다음 인덱스도 추가 검토 대상이다.

```sql
create index idx_check_results_monitor_created_at_desc
on check_results(monitor_id, created_at desc);
```

근거:

- 최신 1건 조회가 `0.122 ms`에서 `0.051 ms`로 개선됐다.
- 1시간 trend 조회가 `1.151 ms`에서 `0.213 ms`로 개선됐다.
- 시간 범위가 좁을수록 효과가 커진다.

다만 24시간 trend에서는 개선이 없었다.
24시간 범위가 대부분의 row를 포함하는 데이터에서는 기존 `monitor_id` 인덱스와 차이가 작다.

## 현재 로컬 DB 상태

측정을 위해 로컬 PostgreSQL에는 다음 후보 인덱스가 생성되어 있다.

```text
idx_check_results_monitor_id_id_desc
idx_check_results_monitor_created_at_desc
```

Flyway migration에는 아직 반영하지 않았다.

## 다음 작업

1. 위 두 인덱스를 Flyway migration으로 추가할지 결정한다.
2. 추가한다면 migration 작성 후 `migrationTest`와 `test`를 실행한다.
3. migration 반영 후 같은 쿼리를 다시 측정해 문서에 최종 결과를 갱신한다.
