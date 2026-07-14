---
documentId: rb-synthetic-db-connection-failure
tenantId: tenant-synthetic-alpha
ownerId: owner-synthetic-database
title: 합성 PostgreSQL 연결 실패 대응
documentType: RUNBOOK
serviceName: member-api
errorTypes:
  - DB_CONNECTION_FAILURE
version: 1
status: ACTIVE
updatedAt: 2026-07-14T00:00:00Z
---

# 합성 PostgreSQL 연결 실패 대응

이 문서는 `src/test/resources/loganalysis/quality/db-connection-failure.log`의 합성 시나리오와 연결된다. 실제 DB 주소, 계정과 접속 문자열을 포함하지 않는다.

## 증상

- `SQLState 08001`과 `Connection refused`가 관측된다.
- pool 지표가 `active=10 idle=0 waiting=7`로 연결 대기 상태를 보인다.

## 영향

- `member-api`의 DB 조회·저장 요청이 실패하거나 대기할 가능성이 있다.
- DB 프로세스 접근 불가와 커넥션 장기 점유가 동시에 후보이며, 현재 로그만으로 하나를 확정하지 않는다.

## 확인 절차

1. DB 서비스 상태와 애플리케이션 실행 위치에서 포트 연결 가능 여부를 읽기 전용으로 확인한다.
2. pool의 active, idle, waiting과 획득 대기 시간을 확인한다.
3. 비밀값을 노출하지 않는 DB 관측 화면에서 장기 실행·대기 쿼리 수를 확인한다.
4. 같은 시간대 배포·네트워크 변경 기록이 있는지 읽기 전용으로 확인한다.

읽기 전용 예시 명령:

```powershell
Test-NetConnection db.example.invalid -Port 5432
```

예시 명령은 연결 가능 여부만 확인하며 자격 증명이나 접속 문자열을 출력하지 않는다.

## 안전한 완화

- 신규 요청 제한이나 비핵심 DB 작업 일시 중지는 영향과 승인 절차를 확인한 뒤 검토한다.
- DB 강제 재시작, 데이터 삭제와 pool 크기 증가는 원인 확인 전에 수행하지 않는다.

## 복구 검증

- 연결 획득 오류가 중단되고 pool waiting이 정상 범위로 감소했는지 확인한다.
- 읽기와 쓰기를 포함한 합성 트랜잭션이 정상 완료되는지 승인된 테스트 경로에서 확인한다.

## 재발 방지

- pool 획득 대기, 장기 점유와 DB 연결 실패율에 경보를 둔다.
- 애플리케이션 동시성과 DB 최대 연결 수를 함께 반영한 용량 기준을 문서화한다.

## 참고 링크

- [품질 평가 기준](../../ai/log-analysis-quality-evaluation.md)
- 합성 fixture: `src/test/resources/loganalysis/quality/db-connection-failure.log`
