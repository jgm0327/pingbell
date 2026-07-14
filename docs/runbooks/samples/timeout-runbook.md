---
documentId: rb-synthetic-timeout
tenantId: tenant-synthetic-alpha
ownerId: owner-synthetic-ops
title: 합성 upstream 연결 timeout 대응
documentType: RUNBOOK
serviceName: order-api
errorTypes:
  - TIMEOUT
version: 1
status: ACTIVE
updatedAt: 2026-07-14T00:00:00Z
---

# 합성 upstream 연결 timeout 대응

이 문서는 `src/test/resources/loganalysis/quality/timeout.log`의 합성 시나리오와 연결된다. 모든 식별자와 주소는 예시이며 실제 운영 정보를 나타내지 않는다.

## 증상

- `inventory-service` 연결에서 3000ms timeout과 `HttpConnectTimeoutException`이 서로 다른 trace에 반복된다.
- `order-api` 요청이 upstream 응답을 기다리다 실패할 수 있다.

## 영향

- 재고 확인을 포함한 요청이 지연되거나 실패할 가능성이 있다.
- 이 로그만으로 네트워크 경로와 upstream 애플리케이션 중 어느 쪽이 원인인지 확정할 수 없다.

## 확인 절차

1. 같은 시간대 `inventory-service` health와 요청 성공률을 읽기 전용으로 확인한다. 기대 결과는 health 상태와 timeout 발생 시점의 상관관계를 확보하는 것이다.
2. 두 서비스 사이 연결 지연, DNS와 연결 실패율 지표를 확인한다. 기대 결과는 애플리케이션 오류와 네트워크 경로 문제를 구분하는 것이다.
3. timeout이 서로 다른 trace에서 반복되는지 확인한다. 기대 결과는 단일 요청 문제인지 지속 문제인지 구분하는 것이다.

읽기 전용 예시 명령:

```text
curl --head --max-time 3 https://inventory.example.invalid/health
```

예시 명령은 자동 실행하지 않으며 운영자가 허용된 진단 endpoint인지 먼저 확인한다.

## 안전한 완화

- 정상 인스턴스로의 트래픽 전환이나 호출 기능 제한은 지표로 영향 범위를 확인하고 운영 승인 후 검토한다.
- 근거 없이 timeout 임계값을 늘리거나 서비스를 재시작하지 않는다.

## 복구 검증

- 연속된 health 요청과 실제 사용자 흐름에서 timeout이 더 발생하지 않는지 확인한다.
- upstream 성공률과 order 응답 시간이 정상 범위로 돌아왔는지 확인한다.

## 재발 방지

- upstream 의존성별 timeout, 연결 실패율과 trace 상관관계를 관측한다.
- timeout 예산과 fallback 정책을 부하 테스트 결과로 검토한다.

## 참고 링크

- [품질 평가 기준](../../ai/log-analysis-quality-evaluation.md)
- 합성 fixture: `src/test/resources/loganalysis/quality/timeout.log`
