## 작업 내용

`docs/planning/implementation-priority.md` 5.2절의 "단일 앱 최소 관측성 지표 구현 범위 재검토"를 진행하다가, 이 항목이 이미 대부분 구현되어 있는 걸 확인했다(`PingbellMetrics`가 health check/incident/notification counter·timer를 이미 기록 중). 문서(`docs/operations/observability-metrics.md`)가 그 사실을 반영 못 하고 있었던 것이 원인. 그래서 이번 PR은 문서를 실제 코드 상태에 맞게 정정하고, 설계 문서 12절 "가장 먼저 볼 지표" 중 유일하게 빠져 있던 gauge 2종(현재 open incident 수, 현재 재시도 대기 알림 수)을 추가로 구현했다.

## 변경 사항

- **`PingbellMetrics`**: `@PostConstruct`로 `pingbell.incident.open.current`, `pingbell.notification.retry.pending.current` 두 gauge를 등록. counter/timer와 달리 이벤트 시점에 기록하는 게 아니라, 조회될 때마다 repository를 다시 조회하도록 등록만 해둔다.
- **`IncidentRepository`**: 전역(전체 tenant) `countByStatus(IncidentStatus)` 추가 — gauge 전용, 기존 `countByMonitorMemberIdAndStatus`(tenant 스코프)와 별개.
- **`NotificationHistoryRepository`**: 전역 `countByStatus(NotificationStatus)` 추가 — 마찬가지로 gauge 전용.
- **`docs/operations/observability-metrics.md`**: 4·6.1·7·8.1·12·15~17절을 실제 구현 상태에 맞게 갱신(어떤 지표가 구현됐고, 무엇이 남았는지 명시).
- **`docs/planning/implementation-priority.md`**: 5.2절의 해당 항목을 완료로 표시.

## API 계약

- 신규/변경 API 없음. `/actuator/metrics/pingbell.incident.open.current`, `/actuator/metrics/pingbell.notification.retry.pending.current`로 조회 가능해짐(기존 `management.endpoints.web.exposure.include: health,metrics` 설정 그대로 사용).
- Frontend 영향 없음.

## 테스트 결과

- [x] `./gradlew.bat compileJava compileTestJava`
- [x] `PingbellMetricsTest`(신규): gauge가 등록 시점 값을 캐시하지 않고 매 조회마다 repository 상태를 다시 반영하는지 확인.
- [x] `IncidentRepositoryTest`(신규), `NotificationHistoryRepositoryTest`(countByStatus 케이스 추가): 전역 count가 특정 tenant로 스코프되지 않고 전체 합계인지 실제 DB로 확인.
- [x] `REDIS_PORT=<로컬 매핑 포트> ./gradlew.bat test` — 전체 테스트 스위트 통과(기존 회귀 없음).

## 수동 테스트 방법

```powershell
docker compose up -d postgres redis
$env:REDIS_PORT="<docker port pingbell-redis 결과>"
.\gradlew.bat test
```

애플리케이션 기동 후 `curl http://localhost:8080/actuator/metrics/pingbell.incident.open.current`, `curl http://localhost:8080/actuator/metrics/pingbell.notification.retry.pending.current`로 값이 나오는지 확인.

## 고민한 점

- gauge는 `Gauge.builder(name, stateObject, valueFunction)`로 등록했다 — repository 자체를 상태 객체로 넘기고, 조회 시점마다 `countByStatus`를 다시 호출하게 했다. repository는 Spring 싱글톤 빈이라 Micrometer의 weak reference로도 GC될 위험이 없다.
- 새 `countByStatus`는 의도적으로 tenant(member) 스코프가 없다 — 운영 전체 상태를 보려는 gauge 전용이라 그렇다. 실수로 사용자용 API 응답에 쓰이지 않도록 repository 인터페이스에 주석으로 용도를 명시했다.
- `retry_due_current`, `retry_exhausted_total`은 이번 범위에서 뺐다 — `docs/operations/observability-metrics.md` 17절에 다음 후보로 남겨뒀다.

## Frontend 영향

해당 없음(Backend observability 전용 작업).

## 관련 이슈

close #
