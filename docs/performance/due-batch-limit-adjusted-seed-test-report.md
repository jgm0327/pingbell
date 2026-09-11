# Due Batch Limit Adjusted Seed Test Report

작성일: 2026-07-10

## 1. 목적

`docs/performance/due-batch-limit-baseline-test-report.md`에서 확인한 왜곡된 due 분포를 보정한 뒤,
동일한 due 조회 쿼리를 PostgreSQL `EXPLAIN (ANALYZE, BUFFERS)`로 다시 측정한다.

이번 문서는 다음 내용을 정리한다.

- 조정된 seed 분포
- 조정 후 due 조회 쿼리 실행 계획
- batch limit 후보 재확인

## 2. 실행 항목

### 2.1 seed 재적재

실행 명령:

```powershell
.\gradlew.bat dbSeedTest
```

결과:

- 성공

seed 테스트 로그 기준 분포:

- `dueMonitors=210 / totalMonitors=10000`
- `retryDueHistories=4000 / totalHistories=100000`

해석:

- seed 전용 데이터 기준 due monitor 비율은 `2.1%`
- seed 전용 데이터 기준 retry due notification 비율은 `4.0%`
- 목표 범위였던 `1~5%` 안에 들어온다.

### 2.2 PostgreSQL 통계 갱신

실행 쿼리:

```sql
analyze monitors;
analyze notification_histories;
```

결과:

- 성공

## 3. 측정 환경

- DB 컨테이너: `pingbell-postgres`
- PostgreSQL 포트: `5432`
- 측정 명령: `EXPLAIN (ANALYZE, BUFFERS)`

## 4. 측정 시점 실제 테이블 분포

### 4.1 due monitor 분포

측정 쿼리:

```sql
select count(*) as total_monitors,
       count(*) filter (
           where status in ('ACTIVE', 'DOWN')
             and deleted_at is null
             and next_check_at <= now()
       ) as due_monitors
from monitors;
```

결과:

- 전체 monitor: `10,002`
- due monitor: `5,845`

해석:

- seed 외 기존 데이터가 일부 섞여 있어 전체 테이블 기준 due 비율은 seed 전용 비율보다 높다.
- 따라서 planner는 seed 전용 로그보다 전체 테이블 상태를 기준으로 선택한다.

### 4.2 retry due notification 분포

측정 쿼리:

```sql
select count(*) as total_histories,
       count(*) filter (
           where retryable = true
             and next_retry_at is not null
             and next_retry_at <= now()
             and retry_count < max_retry_count
       ) as retry_due_histories
from notification_histories;
```

결과:

- 전체 notification history: `100,022`
- retry due history: `4,000`

해석:

- retry due 비율은 전체 테이블 기준으로도 약 `4.0%` 수준이다.
- baseline의 `30.0%` 대비 분포 왜곡은 크게 줄었다.

## 5. 현재 쿼리 재측정 결과

### 5.1 Scheduler due monitor 현재 쿼리

측정 쿼리:

```sql
select *
from monitors
where status in ('ACTIVE', 'DOWN')
  and deleted_at is null
  and next_check_at <= now();
```

결과:

- Plan: `Seq Scan on monitors`
- rows: `6,057`
- rows removed by filter: `3,945`
- execution time: `4.673 ms`

해석:

- 전체 테이블 기준 due 대상이 아직 많아 no-limit 쿼리는 여전히 seq scan을 선택했다.
- monitor due 조회는 여전히 limit을 붙여야 planner 이점을 바로 가져갈 수 있다.

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

결과:

- Plan: `Seq Scan + Sort`
- rows: `4,000`
- rows removed by filter: `96,022`
- sort method: `quicksort`
- execution time: `114.142 ms`

해석:

- retry due 비율은 낮아졌지만 현재 구조에서는 여전히 전체 스캔 후 정렬을 수행한다.
- baseline보다 분포는 현실적이지만, no-limit 비용 자체는 아직 크다.

## 6. batch limit 적용 형태 재측정

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
| 50 | `idx_monitors_due_active` + Incremental Sort | `0.257 ms` |
| 500 | `idx_monitors_due_active` + Incremental Sort | `9.556 ms` |
| 1000 | `idx_monitors_due_active` + Incremental Sort | `7.887 ms` |

해석:

- 조정 후 분포에서도 `order by + limit`를 붙이면 즉시 `idx_monitors_due_active`를 사용한다.
- no-limit `4.673 ms` 대비 `limit 50`은 매우 빠르다.
- `500`, `1000` 측정값은 캐시와 실행 시점 영향으로 일부 흔들리지만, 여전히 index scan 기반으로 동작한다.

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
| 50 | `Seq Scan + top-N heapsort` | `28.689 ms` |
| 100 | `Seq Scan + top-N heapsort` | `49.026 ms` |
| 500 | `Seq Scan + top-N heapsort` | `51.303 ms` |
| 1000 | `Seq Scan + top-N heapsort` | `24.047 ms` |
| 5000 | `Seq Scan + quicksort` | `31.512 ms` |

해석:

- retry due 조회는 분포를 낮춰도 현재 인덱스 구조만으로는 seq scan을 벗어나지 못했다.
- 그래도 no-limit `114.142 ms` 대비 limit 적용 시 실행 시간은 줄어든다.
- `top-N heapsort`가 붙는 구간에서는 결과 건수 제한 효과가 확인된다.
- 일부 수치는 캐시 상태와 실행 시점 영향으로 흔들려, 절대값보다 "no-limit 대비 감소"와 "plan 유지"를 보는 편이 맞다.

## 7. 판단

### 7.1 Scheduler due monitor

- no-limit는 여전히 `Seq Scan`
- `order by + limit`는 일관되게 `idx_monitors_due_active` 사용
- monitor due 조회는 batch limit 적용 효과가 여전히 명확하다.

### 7.2 Notification retry due

- no-limit는 `114.142 ms`
- limit 적용 시 `24~51 ms` 수준으로 감소
- retry due 조회도 batch limit 적용은 유효하지만, monitor보다 개선 폭이 planner 수준에서 덜 극적이다.

## 8. 결론

- 조정된 seed 기준에서도 due 조회 batch limit 적용 필요성은 유지된다.
- monitor due 조회는 `order by + limit`만으로도 즉시 index scan 이점을 얻는다.
- notification retry due 조회는 limit 적용만으로도 비용을 낮출 수 있지만, 현재는 여전히 seq scan 기반이다.
- 따라서 다음 구현 우선순위는 baseline 문서와 동일하게 유지해도 된다.
  - `MonitorRepository` due 조회에 정렬 + limit 적용
  - `NotificationHistoryRepository` retry due 조회에 정렬 + limit 적용
  - batch size를 application 설정으로 분리

## 9. 다음 작업

1. due monitor 조회 batch limit 구현
2. retry due 조회 batch limit 구현
3. batch size 설정값 분리
4. 관련 테스트 보강
5. 이번 측정 결과를 반영해 PR 문서 또는 상태 문서 갱신
