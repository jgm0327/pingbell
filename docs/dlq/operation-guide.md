# DLQ Operation Guide

## 로컬 smoke test 전제 조건

DLQ command smoke test는 실제 Kafka topic을 읽거나, reprocess command의 경우 source topic으로 다시 publish할 수 있다. 로컬에서 다음 조건을 먼저 확인한다.

- Docker Compose로 Kafka를 실행할 수 있어야 한다.
- 애플리케이션은 `kafka` dispatch mode로 실행한다.
- 확인 대상 DLQ topic과 partition / offset을 알고 있어야 한다.
- `list`, `dry-run`, `batch-dry-run`은 record를 다시 publish하지 않는다.
- `reprocess`는 실제 publish가 발생하므로 dry-run 결과와 confirm 옵션을 먼저 확인한다.

## 로컬 smoke test 권장 순서

1. Kafka를 실행한다.
2. 애플리케이션을 `kafka` dispatch mode로 실행한다.
3. Kafka console consumer로 DLQ topic에 record가 있는지 확인한다.
4. `list` command로 record의 partition / offset / payload type을 확인한다.
5. `batch-dry-run` command로 제한된 개수의 record 상태를 한 번에 확인한다.
6. 실제 후보 record 하나를 골라 `dry-run` command를 다시 실행한다.
7. dry-run 결과가 `REPROCESSABLE`이고 DB 상태와 idempotency 기준이 맞을 때만 `reprocess` command를 실행한다.

## Kafka 실행

```powershell
docker compose up -d kafka
```

Kafka mode로 애플리케이션을 실행한다.

```powershell
.\gradlew.bat bootRun --args="--pingbell.check.dispatch-mode=kafka"
```

## Topic별 DLQ 메시지 확인

`HealthCheckRequested` consumer 실패 확인:

```powershell
docker exec -it pingbell-kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic pingbell.health-check.requested.dlq --from-beginning --property print.headers=true
```

`HealthCheckCompleted` consumer 실패 확인:

```powershell
docker exec -it pingbell-kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic pingbell.health-check.completed.dlq --from-beginning --property print.headers=true
```

`NotificationRequested` consumer 실패 확인:

```powershell
docker exec -it pingbell-kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic pingbell.notification.requested.dlq --from-beginning --property print.headers=true
```

확인할 header:

```text
pingbell-dlq-original-topic
pingbell-dlq-original-partition
pingbell-dlq-original-offset
pingbell-dlq-exception
```

확인할 payload id:

| DLQ topic | 확인할 id |
| --- | --- |
| `pingbell.health-check.requested.dlq` | `eventId`, `monitorId`, `memberId`, `scheduledAt` |
| `pingbell.health-check.completed.dlq` | `eventId`, `requestEventId`, `monitorId`, `memberId`, `checkResultId` |
| `pingbell.notification.requested.dlq` | `eventId`, `incidentId`, `monitorId`, `memberId`, `notificationType` |

smoke test 확인 항목:

- `print.headers=true`로 DLQ header가 출력되는지 확인한다.
- topic 이름이 `.dlq` suffix로 끝나는지 확인한다.
- payload에 monitor URL, email, webhook URL, token 원문이 포함되지 않는지 확인한다.
- record가 없다면 command 테스트에 사용할 partition / offset을 먼저 만들거나 다른 DLQ topic을 선택한다.

## DLQ list command

DLQ topic의 record를 제한된 개수만 조회해 topic, partition, offset, payload type, 주요 id, 읽기 가능 여부를 확인한다. 실제 재처리 가능 여부는 판단하지 않고, record를 다시 publish하지 않는다.

코드 기준 property:

| property | 필수 여부 | 기본값 |
| --- | --- | --- |
| `pingbell.dlq.list.enabled` | 필수 | 없음 |
| `pingbell.dlq.list.topic` | 필수 | 없음 |
| `pingbell.dlq.list.max-records` | 선택 | `10` |

```powershell
.\gradlew.bat bootRun --args="--pingbell.dlq.list.enabled=true --pingbell.dlq.list.topic=pingbell.health-check.requested.dlq --pingbell.dlq.list.max-records=10"
```

출력 예시:

```text
DLQ_RECORD topic=pingbell.health-check.requested.dlq partition=0 offset=0 payloadType=HealthCheckRequestedEvent primaryIds="eventId=..., monitorId=10, memberId=1" readStatus=READABLE reason=
DLQ_RECORD topic=pingbell.health-check.requested.dlq partition=0 offset=1 payloadType=INVALID_PAYLOAD primaryIds="" readStatus=UNREADABLE reason=Invalid DLQ payload schema. topic=pingbell.health-check.requested.dlq
DLQ_RECORD topic=pingbell.health-check.requested.dlq partition=0 offset=2 payloadType=TOMBSTONE primaryIds="" readStatus=UNREADABLE reason=DLQ record value is null
DLQ_LIST_SUMMARY topic=pingbell.health-check.requested.dlq requestedMax=10 returned=3
```

`readStatus=UNREADABLE`인 record는 payload를 현재 코드의 event schema로 읽을 수 없다는 뜻이다. invalid payload는 `Invalid DLQ payload schema...`, tombstone record는 `DLQ record value is null`로 표시된다.

smoke test 확인 항목:

- `DLQ_RECORD`가 record별로 출력되는지 확인한다.
- `topic`, `partition`, `offset`, `payloadType`, `primaryIds`, `readStatus`가 출력되는지 확인한다.
- 마지막에 `DLQ_LIST_SUMMARY`가 출력되는지 확인한다.
- 실패하면 `DLQ_LIST_SUMMARY ... status=FAILED reason=...` 로그의 topic과 reason을 먼저 확인한다.

## DLQ dry-run command

DLQ record의 재처리 가능 여부만 확인한다. 이 command는 데이터를 변경하거나 record를 다시 publish하지 않는다.

코드 기준 property:

| property | 필수 여부 | 기본값 |
| --- | --- | --- |
| `pingbell.dlq.dry-run.enabled` | 필수 | 없음 |
| `pingbell.dlq.dry-run.topic` | 필수 | 없음 |
| `pingbell.dlq.dry-run.partition` | 선택 | `0` |
| `pingbell.dlq.dry-run.offset` | 필수 | 없음 |

```powershell
.\gradlew.bat bootRun --args="--pingbell.dlq.dry-run.enabled=true --pingbell.dlq.dry-run.topic=pingbell.health-check.requested.dlq --pingbell.dlq.dry-run.partition=0 --pingbell.dlq.dry-run.offset=0"
```

출력 예시:

```text
DLQ_DRY_RUN topic=pingbell.health-check.requested.dlq partition=0 offset=0 payloadType=HealthCheckRequestedEvent status=REPROCESSABLE reason=HealthCheckRequested can be retried as a dry-run decision
```

status 의미:

| status | 의미 |
| --- | --- |
| `REPROCESSABLE` | 현재 DB 기준으로 재처리 후보가 될 수 있다. |
| `SKIP_ALREADY_PROCESSED` | 이미 처리됐거나 현재 상태가 더 최신이라 재처리하지 않는다. |
| `NOT_REPROCESSABLE` | payload schema 오류, DB 관계 불일치, 삭제/일시정지 등으로 재처리하면 안 된다. |

smoke test 확인 항목:

- `DLQ_DRY_RUN` 로그에 `payloadType`, `status`, `reason`이 출력되는지 확인한다.
- `status=REPROCESSABLE`이어도 바로 reprocess하지 말고 현재 DB 상태와 idempotency 기준을 확인한다.
- 실패하거나 읽을 수 없는 record는 `status=NOT_REPROCESSABLE`과 `reason`을 확인한다.
- dry-run command는 source topic으로 publish하지 않아야 한다.

## DLQ batch dry-run command

DLQ topic의 record를 `max-records` 개수만큼 읽고 각 record에 대해 dry-run 판단만 수행한다. 이 command는 실제 reprocess를 수행하지 않고 source topic으로 publish하지 않는다.

코드 기준 property:

| property | 필수 여부 | 기본값 |
| --- | --- | --- |
| `pingbell.dlq.batch-dry-run.enabled` | 필수 | 없음 |
| `pingbell.dlq.batch-dry-run.topic` | 필수 | 없음 |
| `pingbell.dlq.batch-dry-run.max-records` | 선택 | `10` |

```powershell
.\gradlew.bat bootRun --args="--pingbell.dlq.batch-dry-run.enabled=true --pingbell.dlq.batch-dry-run.topic=pingbell.health-check.requested.dlq --pingbell.dlq.batch-dry-run.max-records=10"
```

출력 예시:

```text
DLQ_BATCH_DRY_RUN topic=pingbell.health-check.requested.dlq partition=0 offset=0 payloadType=HealthCheckRequestedEvent primaryIds="eventId=..., monitorId=10, memberId=1" status=REPROCESSABLE reason=HealthCheckRequested can be retried as a dry-run decision
DLQ_BATCH_DRY_RUN topic=pingbell.health-check.requested.dlq partition=0 offset=1 payloadType=INVALID_PAYLOAD primaryIds="" status=NOT_REPROCESSABLE reason=Invalid DLQ payload schema. topic=pingbell.health-check.requested.dlq
DLQ_BATCH_DRY_RUN topic=pingbell.health-check.requested.dlq partition=0 offset=2 payloadType=TOMBSTONE primaryIds="" status=NOT_REPROCESSABLE reason=DLQ record value is null
DLQ_BATCH_DRY_RUN_SUMMARY topic=pingbell.health-check.requested.dlq requestedMax=10 returned=3 REPROCESSABLE=1 SKIP_ALREADY_PROCESSED=0 NOT_REPROCESSABLE=2
```

invalid payload와 tombstone record는 batch dry-run에서 `NOT_REPROCESSABLE`로 집계된다. 이 경우 `DlqDryRunService`의 DB 기반 판단까지 가지 않고, record 읽기 단계의 오류 사유를 그대로 결과에 남긴다.

smoke test 확인 항목:

- `DLQ_BATCH_DRY_RUN`이 record별로 출력되는지 확인한다.
- `DLQ_BATCH_DRY_RUN_SUMMARY`의 `returned`, `REPROCESSABLE`, `SKIP_ALREADY_PROCESSED`, `NOT_REPROCESSABLE` 집계를 확인한다.
- `max-records`는 처음에는 10으로 유지한다.
- batch dry-run 결과는 후보 선별용이며 실제 publish를 수행하지 않아야 한다.

## 실제 재처리 command

단일 DLQ record만 원본 source topic으로 다시 publish한다. 실행 직전에 dry-run 검증을 다시 수행하며, 결과가 `REPROCESSABLE`이 아니면 publish하지 않는다.

코드 기준 property:

| property | 필수 여부 | 기본값 |
| --- | --- | --- |
| `pingbell.dlq.reprocess.enabled` | 필수 | 없음 |
| `pingbell.dlq.reprocess.topic` | 필수 | 없음 |
| `pingbell.dlq.reprocess.partition` | 선택 | `0` |
| `pingbell.dlq.reprocess.offset` | 필수 | 없음 |
| `confirm-reprocess` | 실제 재처리 시 `true` 필수 | `false` |

```powershell
.\gradlew.bat bootRun --args="--pingbell.dlq.reprocess.enabled=true --pingbell.dlq.reprocess.topic=pingbell.health-check.requested.dlq --pingbell.dlq.reprocess.partition=0 --pingbell.dlq.reprocess.offset=0 --confirm-reprocess=true"
```

출력 예시:

```text
DLQ_REPROCESS topic=pingbell.health-check.requested.dlq partition=0 offset=0 sourceTopic=pingbell.health-check.requested payloadType=HealthCheckRequestedEvent messageKey=10 status=REPROCESSABLE reason=HealthCheckRequested can be retried as a dry-run decision
```

실행 조건:

- `--confirm-reprocess=true`가 있어야 한다.
- topic은 `.dlq`로 끝나야 한다.
- dry-run 결과가 `REPROCESSABLE`이어야 한다.
- service 직접 호출은 하지 않는다.
- source topic은 DLQ topic에서 `.dlq` suffix를 제거해 결정한다.

smoke test 확인 항목:

- 실행 직전 같은 topic / partition / offset으로 dry-run을 다시 수행한다.
- dry-run 결과가 `REPROCESSABLE`인지 확인한다.
- `--confirm-reprocess=true`가 없으면 command가 실패해야 한다.
- 성공 시 `DLQ_REPROCESS` 로그의 `sourceTopic`, `payloadType`, `messageKey`, `status`, `reason`을 확인한다.
- re-publish 이후 source topic consumer 로그와 DB 상태로 실제 처리 결과를 확인한다.

## 운영 실행 순서

1. Kafka와 애플리케이션을 `kafka` dispatch mode로 실행한다.
2. `list` command로 DLQ topic에 쌓인 record의 partition / offset / payload type / 주요 id를 확인한다.
3. 필요하면 `batch-dry-run` command로 제한된 개수의 record를 한 번에 점검한다.
4. 실제 재처리가 필요한 record 후보를 하나 고른 뒤 `dry-run` command를 단일 partition / offset 기준으로 다시 실행한다.
5. dry-run 결과가 `REPROCESSABLE`이고 현재 DB 상태와 idempotency 기준을 확인한 경우에만 단일 `reprocess` command를 실행한다.
6. `SKIP_ALREADY_PROCESSED`, `NOT_REPROCESSABLE`, invalid payload, tombstone record는 실제 reprocess 대상에서 제외한다.

`max-records`는 운영자가 한 번에 검토할 수 있는 개수로 제한한다. 기본값은 10이며, 원인 분석 중에는 10에서 시작하고 필요한 경우 50 이하로만 늘린다.
