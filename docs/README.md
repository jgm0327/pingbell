# Pingbell 문서 안내

Pingbell 문서는 목적에 따라 다음 디렉터리로 구분한다.

| 경로 | 내용 |
| --- | --- |
| `agents/` | 작업 유형별 AI Agent 지침 |
| `ai/` | 로그 분석, Runbook RAG, 임베딩 설계 |
| `architecture/` | 이벤트 경계, Kafka 흐름, Worker 분리 설계 |
| `database/` | Flyway, 스키마 변경, pgvector 마이그레이션 |
| `dlq/` | DLQ 실패 분류, 보안, 재처리 및 운영 절차 |
| `operations/` | 관측성과 운영 지표 |
| `performance/` | 데이터베이스 성능 측정 및 테스트 보고서 |
| `planning/` | 구현 우선순위와 개선 계획 |
| `policies/` | 장애, 알림, 테스트, 마이그레이션 정책 |
| `portfolio/` | 문제 해결 과정과 포트폴리오 자료 |
| `release-notes/` | MVP 단계별 릴리스 노트 |
| `runbooks/` | Runbook 작성 규격과 샘플 |
| `status/` | 작업 시점별 프로젝트 상태 기록 |
| `tistory/` | 기술 블로그 초안과 게시용 원고 |
| `troubleshooting/` | 문제 진단 및 해결 절차 |

## 루트 유지 문서

- `next_feature_request.md`: 다음 구현 작업의 공통 진입점
- `current_pull_request_template.md`: 현재 작업의 Pull Request 정리

두 문서는 `AGENTS.md`의 작업 흐름에서 고정 경로로 참조하므로 `docs/` 루트에 유지한다.
