# DLQ Operation Logs

## 운영 로그 보관 기준

현재 단계에서는 DLQ command 실행 결과를 별도 table에 저장하지 않는다. 운영자는 애플리케이션 표준 출력 또는 운영 로그 수집 도구에서 `DLQ_*` operation log를 검색해 record 흐름을 추적한다.

공통 보관 원칙:

- 모든 DLQ command 로그는 한 줄 key-value 형식으로 남긴다.
- record 식별자는 `topic`, `partition`, `offset` 조합을 기준으로 한다.
- payload 식별자는 `payloadType`과 `primaryIds`를 기준으로 한다.
- command 결과는 `status`, `readStatus`, `reason` 중 해당 command가 제공하는 필드로 판단한다.
- `reason`은 운영 판단에 필요한 짧은 사유만 남기고 payload 원문이나 민감 정보를 포함하지 않는다.
- 같은 record의 흐름은 `topic + partition + offset`으로 `DLQ_RECORD`, `DLQ_BATCH_DRY_RUN`, `DLQ_DRY_RUN`, `DLQ_REPROCESS` 로그를 함께 조회한다.
- batch command summary는 개별 record 추적용이 아니라 실행 범위와 집계 확인용으로 보관한다.

## Command별 필수 로그 필드

| 로그 | 목적 | 성공 필드 | 실패 필드 | 추적 기준 |
| --- | --- | --- | --- | --- |
| `DLQ_RECORD` | DLQ topic record 목록 확인 | `topic`, `partition`, `offset`, `payloadType`, `primaryIds`, `readStatus`, `reason` | 해당 없음 | 특정 record가 읽을 수 있는 payload인지 확인한다. |
| `DLQ_LIST_SUMMARY` | list command 실행 범위 확인 | `topic`, `requestedMax`, `returned` | `topic`, `requestedMax`, `status=FAILED`, `reason` | list command가 몇 개 record를 조회했는지 확인한다. |
| `DLQ_BATCH_DRY_RUN` | 여러 record의 재처리 가능성 사전 판단 | `topic`, `partition`, `offset`, `payloadType`, `primaryIds`, `status`, `reason` | 해당 없음 | batch 검토 중 재처리 후보를 고른다. |
| `DLQ_BATCH_DRY_RUN_SUMMARY` | batch dry-run 집계 확인 | `topic`, `requestedMax`, `returned`, `REPROCESSABLE`, `SKIP_ALREADY_PROCESSED`, `NOT_REPROCESSABLE` | `topic`, `requestedMax`, `status=FAILED`, `reason` | 전체 batch 결과 비율과 실패 여부를 확인한다. |
| `DLQ_DRY_RUN` | 단일 record 재처리 가능성 최종 판단 | `topic`, `partition`, `offset`, `payloadType`, `status`, `reason` | `topic`, `partition`, `offset`, `status=NOT_REPROCESSABLE`, `reason` | 실제 reprocess 직전 단일 record 상태를 확인한다. |
| `DLQ_REPROCESS` | 단일 record 실제 재처리 실행 결과 | `topic`, `partition`, `offset`, `sourceTopic`, `payloadType`, `messageKey`, `status`, `reason` | `topic`, `partition`, `offset`, `status=FAILED`, `reason` | 어떤 DLQ record가 어느 source topic으로 다시 publish됐는지 확인한다. |

`status=FAILED`인 summary 또는 reprocess 로그는 command 실행 자체가 실패했다는 뜻이다. 이 경우 같은 `topic`, `partition`, `offset`으로 `list`와 `dry-run`을 다시 실행하기 전에 실패 원인을 먼저 보정한다.

## Topic별 primaryIds 기준

`primaryIds`는 payload 원문을 로그에 남기지 않고도 DB source of truth를 다시 조회하기 위한 최소 식별자다.

| payloadType | primaryIds | DB 확인 기준 |
| --- | --- | --- |
| `HealthCheckRequestedEvent` | `eventId`, `monitorId`, `memberId` | monitor 존재 여부, monitor owner, monitor status, `nextCheckAt` 충돌 여부 |
| `HealthCheckCompletedEvent` | `eventId`, `requestEventId`, `monitorId`, `memberId`, `checkResultId` | check result 존재 여부, monitor 관계, incident 판정 처리 여부 |
| `NotificationRequestedEvent` | `eventId`, `incidentId`, `monitorId`, `memberId`, `notificationType` | incident 존재 여부, monitor/member 관계, 같은 incident와 notification type의 알림 이력 여부 |

invalid payload와 tombstone record는 `primaryIds`가 비어 있을 수 있다. 이 경우 `topic`, `partition`, `offset`, `payloadType`, `reason`만으로 record를 식별하고 실제 reprocess 대상에서 제외한다.

## 민감 정보 제외 기준

DLQ command 로그, summary 로그, reason, primaryIds는 [payload-security.md](payload-security.md)의 민감 정보 제외 기준을 따른다. 특히 다음 값은 로그에 남기지 않는다.

- monitor URL 원문
- email address 원문
- Slack webhook URL
- Discord webhook URL
- 복호화된 notification target
- JWT, API key, SMTP password, Authorization header
- 사용자 비밀번호 또는 인증 토큰
- payload 원문 전체

로그 추적을 위해 남겨도 되는 값:

- Kafka `topic`, `partition`, `offset`
- source topic 이름
- event id와 도메인 id
- enum 값인 `payloadType`, `notificationType`, `status`, `readStatus`
- secret을 포함하지 않는 exception class 또는 짧은 실패 분류

운영자가 원인 분석을 위해 실제 URL, email, webhook target이 필요하면 로그가 아니라 DB와 secret 저장소의 권한 있는 조회 절차를 사용한다.

## Record 처리 흐름 추적 방법

단일 record를 추적할 때는 다음 순서로 운영 로그를 조회한다.

1. `DLQ_RECORD topic={topic} partition={partition} offset={offset}` 로그로 payloadType, primaryIds, readStatus를 확인한다.
2. `DLQ_BATCH_DRY_RUN topic={topic} partition={partition} offset={offset}` 로그가 있으면 batch 검토 당시 status와 reason을 확인한다.
3. 실제 재처리 후보라면 `DLQ_DRY_RUN topic={topic} partition={partition} offset={offset}` 로그로 최신 DB 기준 status를 확인한다.
4. 재처리를 실행했다면 `DLQ_REPROCESS topic={topic} partition={partition} offset={offset}` 로그로 sourceTopic, messageKey, status를 확인한다.
5. re-publish 이후 consumer 처리 결과는 source topic consumer 로그와 도메인 DB 상태로 확인한다.

추적 예시:

```text
DLQ_RECORD topic=pingbell.health-check.completed.dlq partition=0 offset=4 payloadType=HealthCheckCompletedEvent primaryIds="eventId=... requestEventId=... monitorId=10 memberId=1 checkResultId=30" readStatus=READABLE reason=
DLQ_BATCH_DRY_RUN topic=pingbell.health-check.completed.dlq partition=0 offset=4 payloadType=HealthCheckCompletedEvent primaryIds="eventId=... requestEventId=... monitorId=10 memberId=1 checkResultId=30" status=REPROCESSABLE reason=HealthCheckCompleted can be retried as a dry-run decision
DLQ_DRY_RUN topic=pingbell.health-check.completed.dlq partition=0 offset=4 payloadType=HealthCheckCompletedEvent status=REPROCESSABLE reason=HealthCheckCompleted can be retried as a dry-run decision
DLQ_REPROCESS topic=pingbell.health-check.completed.dlq partition=0 offset=4 sourceTopic=pingbell.health-check.completed payloadType=HealthCheckCompletedEvent messageKey=10 status=REPROCESSABLE reason=HealthCheckCompleted can be retried as a dry-run decision
```

## 운영 로그만 사용하는 범위와 한계

운영 로그만으로 충분한 범위:

- 특정 DLQ record의 topic / partition / offset 식별
- payload type과 주요 id 확인
- record read 가능 여부 확인
- dry-run 결과와 실제 reprocess 실행 여부 확인
- batch dry-run의 재처리 후보 수 집계

운영 로그만으로 부족한 범위:

- 운영자별 실행자 추적
- 같은 record의 장기 재처리 시도 횟수 집계
- reprocess 승인 이력 저장
- UI에서 DLQ 처리 상태 조회
- command 로그가 보관 기간을 지나 삭제된 뒤의 사후 감사
- DB 보정 작업과 DLQ reprocess 사이의 원자적 audit trail 보장

따라서 다음 조건이 생기면 DLQ table 또는 별도 audit table 도입을 다시 검토한다.

- batch reprocess를 실제로 제공한다.
- 운영자 권한별 승인/실행 이력이 필요하다.
- DLQ 처리 상태를 UI/API로 조회해야 한다.
- 같은 record에 대한 여러 번의 dry-run/reprocess 시도를 장기간 보관해야 한다.
- 장애 사후 분석을 위해 로그 보관 기간보다 긴 audit trail이 필요하다.
