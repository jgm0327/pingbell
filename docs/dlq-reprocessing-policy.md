# Pingbell DLQ / Reprocessing Policy

작성일: 2026-07-01

이 문서는 DLQ 운영 정책의 진입점이다. 상세 내용은 `docs/dlq` 폴더의 주제별 문서를 참고한다.

## 빠른 목차

| 문서 | 내용 |
| --- | --- |
| [overview.md](dlq/overview.md) | 목적, 현재 원칙, 용어 |
| [failure-classification.md](dlq/failure-classification.md) | 실패 분류와 DLQ 후보 |
| [payload-security.md](dlq/payload-security.md) | payload와 민감 정보 기준 |
| [reprocessing-policy.md](dlq/reprocessing-policy.md) | 재처리 판단과 실행 정책 |
| [operation-guide.md](dlq/operation-guide.md) | command 실행 절차 |
| [operation-logs.md](dlq/operation-logs.md) | 로그 필드와 추적 방법 |

## 현재 결론

- 현재 Pingbell은 별도 Worker 앱과 DLQ 운영 UI/API 없이 단일 Spring Boot 앱 구조를 유지한다.
- DLQ record는 원본 Kafka event payload를 유지하고, header에는 원본 topic / partition / offset / exception class만 남긴다.
- 실제 재처리는 dry-run 결과가 `REPROCESSABLE`인 단일 DLQ record만 `--confirm-reprocess=true` 옵션으로 source topic에 다시 publish한다.
- batch reprocess, DLQ table, 운영 UI/API, 자동 재처리 스케줄러는 이번 범위가 아니다.

## 이번 문서 분리에서 하지 않은 일

- 정책 내용을 코드로 구현하지 않았다.
- Kafka topic 구조를 변경하지 않았다.
- DB schema를 변경하지 않았다.
- command 실행 방식과 property 이름을 변경하지 않았다.
