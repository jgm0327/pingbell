---
documentId: rb-synthetic-out-of-memory
tenantId: tenant-synthetic-alpha
ownerId: owner-synthetic-platform
title: 합성 Java heap 메모리 부족 대응
documentType: RUNBOOK
serviceName: report-api
errorTypes:
  - OUT_OF_MEMORY
version: 1
status: ACTIVE
updatedAt: 2026-07-14T00:00:00Z
---

# 합성 Java heap 메모리 부족 대응

이 문서는 `src/test/resources/loganalysis/quality/out-of-memory.log`의 합성 시나리오와 연결된다. 실제 사용자 데이터나 보고서 내용을 사용하지 않는다.

## 증상

- heap이 `usedMb=1870 maxMb=2048`에 도달하고 GC overhead가 관측된다.
- `OutOfMemoryError: Java heap space`가 `ExportService.buildWorkbook` 흐름에서 발생한다.

## 영향

- 대용량 보고서 생성 요청이 실패하고 같은 프로세스의 다른 요청도 지연될 가능성이 있다.
- 입력 크기, 동시 요청, 높은 allocation rate와 메모리 보유 중 어느 항목이 주원인인지는 추가 지표가 필요하다.

## 확인 절차

1. 오류 전후 heap 사용량, GC pause, allocation rate와 동시 보고서 요청 수를 확인한다.
2. 개인정보와 보고서 원문 없이 입력 행 수와 생성 파일 크기 분포를 확인한다.
3. 승인된 진단 절차가 있을 때 heap histogram 또는 heap dump 확보 가능 여부와 저장 공간을 먼저 확인한다.

읽기 전용 예시 명령:

```text
jcmd <PID> GC.heap_info
```

예시 명령은 대상 JVM과 진단 권한을 확인한 운영자가 수동 실행한다. 프로세스 종료나 파일 삭제 명령은 포함하지 않는다.

## 안전한 완화

- 대용량 보고서 동시 실행 제한은 영향 범위와 승인 절차를 확인한 뒤 검토한다.
- 프로세스 강제 종료, 로그·데이터 삭제와 근거 없는 heap 증가는 먼저 수행하지 않는다.

## 복구 검증

- OOM과 과도한 GC가 중단되고 heap이 지속적으로 회수되는지 확인한다.
- 작은 합성 보고서와 경계 크기 요청이 정상 완료되는지 확인한다.

## 재발 방지

- 입력 크기 상한, streaming 생성과 동시성 제한을 부하 테스트로 검토한다.
- heap, GC와 보고서 크기의 상관관계를 관측하고 용량 기준을 기록한다.

## 참고 링크

- [품질 평가 기준](../../ai/log-analysis-quality-evaluation.md)
- 합성 fixture: `src/test/resources/loganalysis/quality/out-of-memory.log`
