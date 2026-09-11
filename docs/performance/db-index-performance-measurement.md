# DB Index Performance Measurement

작성일: 2026-07-08

## 목적

`docs/next_feature_request.md`의 DB 성능 개선 작업을 위해 주요 조회 쿼리를 PostgreSQL seed 데이터에서 직접 측정했다.
이번 문서는 Flyway migration 추가 전, 후보 인덱스가 실제 실행 계획과 실행 시간에 의미 있는 개선을 만드는지 확인한 결과다.

## 테스트 환경

- DB: Docker container `pingbell-postgres`
- PostgreSQL: 16.x
- 테스트 DB: `pingbell`
- 측정 명령: `EXPLAIN (ANALYZE, BUFFERS)`
- 대표 파라미터
  - `member_id = 202`
  - `monitor_id = 20004`

## Seed 데이터 규모

| table | rows |
| --- | ---: |
| member | 100 |
| monitors | 10,000 |
| check_results | 100,000 |
| incidents | 10,000 |
| notification_histories | 100,000 |

## 테스트한 후보 인덱스

```sql
create index idx_monitors_due_check
on monitors(status, next_check_at)
where deleted_at is null;

create index idx_check_results_monitor_id_id_desc
on check_results(monitor_id, id desc);

create index idx_check_results_monitor_created_at_desc
on check_results(monitor_id, created_at desc);

create index idx_notification_histories_retry_due
on notification_histories(next_retry_at, id)
where retryable = true;

create index idx_notification_histories_channel_status_id_desc
on notification_histories(channel_id, status, id desc);
```

추가로 retry due 쿼리에 대해 더 좁은 partial index도 별도로 확인했다.

```sql
create index idx_notification_histories_retry_due_ready
on notification_histories(next_retry_at, id)
where retryable = true
  and retry_count < max_retry_count;
```

## 결과 요약

| 대상 쿼리 | Before plan | Before time | After plan | After time | 판단 |
| --- | --- | ---: | --- | ---: | --- |
| Scheduler due monitor | Seq Scan | 4.006 ms | Seq Scan | 4.619 ms | 추가 비추천 |
| CheckResult 목록 pagination | `idx_check_results_monitor_id` + Sort | 0.059 ms | `idx_check_results_monitor_id` + Sort | 0.151 ms | 추가 비추천 |
| CheckResult 최신 1건 | `idx_check_results_monitor_id` + top-N sort | 0.081 ms | `idx_check_results_monitor_created_at_desc` | 0.058 ms | 조건부 보류 |
| CheckResult monitor 24h trend | `idx_check_results_monitor_id` | 0.102 ms | `idx_check_results_monitor_id` | 0.239 ms | 추가 비추천 |
| CheckResult member 24h trend | `idx_check_results_monitor_id` nested loop | 2.349 ms | `idx_check_results_monitor_created_at_desc` index only scan | 1.365 ms | 조건부 검토 |
| Notification retry due | Seq Scan + external sort | 48.839 ms | retry due index + external sort | 45.620 ms | 현재 후보는 비추천 |
| NotificationHistory member list | PK backward scan | 1.750 ms | PK backward scan | 1.131 ms | 추가 비추천 |
| NotificationHistory member + status list | PK backward scan | 0.534 ms | channel/status index + sort | 1.191 ms | 추가 비추천 |

## 쿼리별 상세

### 1. Scheduler due monitor

테스트 쿼리:

```sql
select *
from monitors
where status in ('ACTIVE', 'DOWN')
  and deleted_at is null
  and next_check_at <= now();
```

Before:

```text
Seq Scan on monitors
Filter: deleted_at is null, status in ACTIVE/DOWN, next_check_at <= now()
Rows: 9501
Rows Removed by Filter: 501
Execution Time: 4.006 ms
```

After:

```text
Seq Scan on monitors
Rows: 9501
Rows Removed by Filter: 501
Execution Time: 4.619 ms
```

판단:

현재 seed 데이터에서는 due 대상이 10,000건 중 9,501건으로 약 95%다.
조건 선택도가 너무 낮아서 PostgreSQL이 인덱스보다 Seq Scan을 선택한다.
이 데이터 분포 기준으로 `idx_monitors_due_check`는 migration에 추가하지 않는다.

추가 검증이 필요하다면 due monitor가 1~5% 정도만 나오도록 seed 분포를 바꾼 뒤 재측정해야 한다.

### 2. CheckResult 목록 pagination

테스트 쿼리:

```sql
select *
from check_results
where monitor_id = 20004
order by id desc
limit 20;
```

Before:

```text
Index Scan using idx_check_results_monitor_id
Sort Key: id desc
Rows: 10
Execution Time: 0.059 ms
```

After:

```text
Index Scan using idx_check_results_monitor_id
Sort Key: id desc
Rows: 10
Execution Time: 0.151 ms
```

판단:

기존 `idx_check_results_monitor_id`만으로 충분히 빠르다.
현재 seed 데이터는 monitor당 check result가 약 10건이라 `(monitor_id, id desc)` 복합 인덱스의 효과가 드러나지 않는다.
현재 데이터 기준으로는 추가하지 않는다.

### 3. CheckResult 최신 1건

테스트 쿼리:

```sql
select *
from check_results
where monitor_id = 20004
order by created_at desc
limit 1;
```

Before:

```text
Index Scan using idx_check_results_monitor_id
Sort Key: created_at desc
Sort Method: top-N heapsort
Execution Time: 0.081 ms
```

After:

```text
Index Scan using idx_check_results_monitor_created_at_desc
Execution Time: 0.058 ms
```

판단:

새 인덱스가 사용되며 sort가 제거된다.
다만 절대 실행 시간이 이미 매우 작고, 현재 seed 데이터는 monitor당 row 수가 적다.
특정 monitor에 check result가 많이 쌓이는 분포로 재측정한 뒤 추가 여부를 결정하는 것이 좋다.

### 4. CheckResult 24시간 trend

Monitor 기준 쿼리:

```sql
select date_trunc('hour', created_at) as bucket,
       sum(case when status = 'SUCCESS' then 1 else 0 end),
       sum(case when status <> 'SUCCESS' then 1 else 0 end),
       avg(response_time_ms)
from check_results
where monitor_id = 20004
  and created_at >= now() - interval '24 hours'
  and created_at < now()
group by bucket
order by bucket;
```

Before:

```text
Index Scan using idx_check_results_monitor_id
Rows: 9
Execution Time: 0.102 ms
```

After:

```text
Index Scan using idx_check_results_monitor_id
Rows: 9
Execution Time: 0.239 ms
```

Member 기준 쿼리:

```sql
select date_trunc('hour', cr.created_at) as bucket,
       count(*)
from check_results cr
join monitors m on m.id = cr.monitor_id
where m.user_id = 202
  and cr.created_at >= now() - interval '24 hours'
  and cr.created_at < now()
group by bucket
order by bucket;
```

Before:

```text
Nested Loop
Bitmap Index Scan using idx_monitors_user_id
Index Scan using idx_check_results_monitor_id
Rows: 854
Execution Time: 2.349 ms
```

After:

```text
Nested Loop
Bitmap Index Scan using idx_monitors_user_id
Index Only Scan using idx_check_results_monitor_created_at_desc
Rows: 850
Heap Fetches: 0
Execution Time: 1.365 ms
```

판단:

`idx_check_results_monitor_created_at_desc`는 member 기준 trend에서 index only scan으로 바뀌며 개선이 있었다.
하지만 현재 dashboard trend 쿼리 전체를 위해 추가할 만큼 충분한지 판단하려면 사용자별 monitor 수와 monitor별 check result 누적량이 더 현실적인 데이터로 재측정되어야 한다.

### 5. Notification retry due

테스트 쿼리:

```sql
select *
from notification_histories
where retryable = true
  and next_retry_at <= now()
  and retry_count < max_retry_count
order by next_retry_at asc, id asc;
```

Before:

```text
Seq Scan on notification_histories
Rows: 30000
Rows Removed by Filter: 70022
Sort Method: external merge
Execution Time: 48.839 ms
```

After, `idx_notification_histories_retry_due`:

```text
Bitmap Heap Scan on notification_histories
Bitmap Index Scan using idx_notification_histories_retry_due
Rows: 30000
Sort Method: external merge
Execution Time: 45.620 ms
```

After, `idx_notification_histories_retry_due_ready`:

```text
Bitmap Heap Scan on notification_histories
Bitmap Index Scan using idx_notification_histories_retry_due_ready
Rows: 30000
Sort Method: external merge
Execution Time: 51.180 ms
```

판단:

인덱스는 사용되지만 due 대상이 30,000건으로 전체의 약 30%다.
반환 row 수가 많고 wide row를 정렬하므로 external sort가 계속 발생한다.
현재 후보 인덱스는 migration에 추가하지 않는다.

이 쿼리는 인덱스보다 먼저 다음 중 하나를 검토하는 편이 낫다.

- retry due 대상이 낮은 비율이 되도록 실제 운영 분포에 가까운 seed 재구성
- scheduler 처리량 기준 `limit` 도입 검토
- 필요한 column만 먼저 조회한 뒤 상세 조회하는 구조 검토

단, 위 구조 변경은 이번 인덱스 검증 작업 범위를 넘는다.

### 6. NotificationHistory member list

테스트 쿼리:

```sql
select h.*
from notification_histories h
join notification_channels c on c.id = h.channel_id
where c.member_id = 202
order by h.id desc
limit 20;
```

Before:

```text
Index Scan Backward using notification_histories_pkey
Rows scanned from history: 2000
Execution Time: 1.750 ms
```

After:

```text
Index Scan Backward using notification_histories_pkey
Rows scanned from history: 2000
Execution Time: 1.131 ms
```

판단:

후보 인덱스가 사용되지 않았다.
현재 데이터 분포에서는 PK backward scan으로 최신 row를 찾는 방식이 충분히 빠르다.

### 7. NotificationHistory member + status list

테스트 쿼리:

```sql
select h.*
from notification_histories h
join notification_channels c on c.id = h.channel_id
where c.member_id = 202
  and h.status = 'FAILED'
order by h.id desc
limit 20;
```

Before:

```text
Index Scan Backward using notification_histories_pkey
Filter: status = FAILED
Execution Time: 0.534 ms
```

After:

```text
Bitmap Index Scan using idx_notification_histories_channel_status_id_desc
Sort Key: h.id desc
Execution Time: 1.191 ms
```

판단:

후보 인덱스는 사용되지만 오히려 느려졌다.
현재 seed 데이터에서는 최신순 PK scan이 더 유리하다.
`idx_notification_histories_channel_status_id_desc`는 추가하지 않는다.

## 최종 판단

현재 seed 데이터 기준으로 바로 Flyway migration에 추가할 인덱스는 없다.

그나마 가능성이 있는 후보는 다음 하나다.

```sql
create index idx_check_results_monitor_created_at_desc
on check_results(monitor_id, created_at desc);
```

하지만 이 인덱스도 현재 데이터에서는 최신 1건 조회와 member trend에서만 소폭 개선이 확인됐다.
monitor별 check result가 더 많이 쌓이는 데이터 분포로 재측정한 뒤 migration 반영 여부를 결정하는 것이 좋다.

## 다음 액션

1. seed 데이터 분포를 운영 시나리오에 더 가깝게 조정한다.
   - due monitor 비율: 1~5%
   - retry due notification 비율: 1~5%
   - 일부 monitor에 check result를 집중 적재
2. 같은 쿼리들을 다시 `EXPLAIN (ANALYZE, BUFFERS)`로 측정한다.
3. 실행 계획에서 실제로 인덱스를 사용하고, 실행 시간이나 scan row 수가 의미 있게 줄어든 인덱스만 Flyway migration으로 추가한다.
