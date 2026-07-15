# Pingbell Runbook 문서 규격

## 1. 목적과 현재 범위

이 규격은 향후 로그 분석 RAG에서 검색 근거로 사용할 Runbook을 같은 구조와 보안 기준으로 작성하기 위한 문서다.

현재 완료된 범위는 문서 규격, 소유권·버전·폐기 정책과 합성 샘플뿐이다. PostgreSQL `pgvector`, embedding 생성·저장, 문서 API, 실제 검색과 LLM 연결은 아직 구현하지 않았다.

## 2. 필수 메타데이터

Runbook은 UTF-8 Markdown과 YAML front matter를 사용한다. 아래 필드는 모두 필수다.

| 필드 | 형식 | 의미와 규칙 |
|---|---|---|
| `documentId` | 문자열 | 같은 Runbook 계보에서 유지하는 논리 식별자다. Tenant 안에서 유일해야 한다. |
| `tenantId` | 문자열 | 문서를 검색하고 읽을 수 있는 Tenant 경계다. 인증된 주체로부터 서버가 결정하며 클라이언트 입력을 신뢰하지 않는다. |
| `ownerId` | 문자열 | 내용 검토와 폐기를 책임지는 소유자 식별자다. `tenantId`를 대체하지 않으며 검색 권한 조건으로 단독 사용하지 않는다. |
| `title` | 문자열 | 사용자에게 표시할 문서 제목이다. |
| `documentType` | enum | 현재 허용 값은 `RUNBOOK`이다. 향후 `POSTMORTEM`은 별도 규격을 정한 뒤 추가한다. |
| `serviceName` | 문자열 | fixture 또는 로그의 `service`와 비교할 정규화된 서비스 이름이다. |
| `errorTypes` | 문자열 배열 | `TIMEOUT`, `DB_CONNECTION_FAILURE`, `HTTP_5XX`, `OUT_OF_MEMORY`처럼 검색 필터에 사용할 정규화 값이다. |
| `version` | 양의 정수 | 같은 `documentId` 안에서 1부터 증가하는 불변 revision 번호다. 기존 revision을 덮어쓰지 않는다. |
| `status` | enum | `DRAFT`, `ACTIVE`, `SUPERSEDED`, `RETIRED` 중 하나다. 검색 가능한 값은 `ACTIVE`뿐이다. |
| `updatedAt` | ISO 8601 UTC | 해당 revision의 마지막 내용 변경 시각이다. 예: `2026-07-14T00:00:00Z`. |

권장 선택 필드는 `reviewedAt`, `reviewDueAt`, `changeSummary`, `supersedesVersion`이다. 선택 필드가 없어도 검색 권한이나 버전 판단이 달라져서는 안 된다.

예시:

```yaml
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
```

예시 식별자는 실제 Tenant나 계정을 나타내지 않는다.

## 3. 본문 필수 구조

모든 Runbook은 아래 제목과 순서를 유지한다.

1. `증상`: 관측 가능한 상태 코드, 예외, 지연 또는 지표를 적는다.
2. `영향`: 사용자와 의존 서비스에 가능한 영향을 적고 확인되지 않은 범위는 단정하지 않는다.
3. `확인 절차`: 읽기 전용 진단을 우선순위대로 적고 각 단계의 기대 결과를 함께 쓴다.
4. `안전한 완화`: 승인이 필요한 변경임을 밝히고 자동 실행을 전제하지 않는다. 근거 없이 재시작·삭제·임계값 증가를 제안하지 않는다.
5. `복구 검증`: 같은 오류의 중단, health 상태, 지표와 사용자 흐름을 함께 확인한다.
6. `재발 방지`: 사후 검토할 관측·용량·코드 개선 항목을 적는다.
7. `참고 링크`: 공개 문서 또는 저장소 안의 비민감 자료만 연결한다. 비밀값이 포함된 URL과 운영 관리 화면 링크는 넣지 않는다.

명령이 필요하면 읽기 전용 단일 진단 명령만 예시로 제공한다. 명령은 운영자가 대상과 권한을 확인한 뒤 수동 실행하며, 재시작·배포·롤백·데이터 변경·삭제와 파이프 또는 연산자를 이용한 복합 명령은 허용하지 않는다.

## 4. 소유권과 Tenant 검색 경계

- `tenantId`는 인증된 사용자와 서버의 권한 모델에서 계산한다. request body, query parameter 또는 모델이 만든 값으로 대체하지 않는다.
- 현재 사용자 단위 소유 모델을 사용한다면 사용자 식별자를 내부 Tenant 경계로 매핑할 수 있지만, API와 Repository에서는 이를 명시적인 권한 조건으로 취급한다.
- 검색은 먼저 `tenant_id = authenticatedTenantId`와 `status = ACTIVE`를 적용해 후보 집합을 제한한 뒤 metadata 또는 vector 유사도를 계산한다.
- 캐시, embedding, chunk와 검색 결과에도 원본 문서의 `tenantId`, `documentId`, `version`을 유지한다.
- 문서 ID 직접 조회, 재정렬, fallback 검색과 reference 조회에서도 같은 Tenant 조건을 다시 적용한다.
- `ownerId`가 같아도 `tenantId`가 다르면 검색하거나 반환하지 않는다. 전역 fallback과 다른 Tenant 문서로 결과 수를 채우는 동작은 금지한다.

## 5. 버전, 활성화와 폐기 정책

| 상태 | 의미 | 검색 포함 여부 |
|---|---|---|
| `DRAFT` | 작성·검토 중이며 운영 근거로 승인되지 않음 | 제외 |
| `ACTIVE` | 소유자가 검토하고 현재 사용할 수 있도록 승인함 | 포함 |
| `SUPERSEDED` | 더 높은 활성 version으로 대체된 과거 revision | 제외 |
| `RETIRED` | 서비스 폐기, 절차 무효 또는 보안 사유로 사용 중지됨 | 제외 |

- 같은 `tenantId`와 `documentId`에는 동시에 하나의 `ACTIVE` version만 허용한다.
- 새 version을 활성화할 때 기존 `ACTIVE` revision은 원자적으로 `SUPERSEDED`로 전환한다.
- 검색은 `ACTIVE`만 대상으로 하고, 그 안에서도 `documentId`별 가장 높은 version인지 검증한다.
- 서비스가 사라지거나 절차가 더는 안전하지 않으면 대체 문서 없이 `RETIRED`로 전환한다.
- revision 내용은 덮어쓰지 않는다. 수정은 version을 증가시킨 새 revision으로 작성해 감사 가능한 이력을 유지한다.
- `reviewDueAt`이 지난 문서는 자동으로 신뢰하지 않는다. 후속 구현에서는 검색 제외 또는 명시적 stale 경고 중 하나를 정책으로 확정해야 하며, 확정 전에는 제외하는 보수적 기준을 사용한다.
- 폐기는 검색 제외를 뜻한다. 데이터의 물리 삭제와 보존 기간은 문서 API 이슈에서 별도로 정한다.

## 6. 민감정보 기준

다음 값은 본문, front matter, 참고 링크, 코드 블록과 예시 출력에 넣지 않는다.

- JWT, Authorization/Cookie/Session 값
- 비밀번호, API Key, webhook secret, 인증서 private key
- DB 전체 접속 문자열, 실제 host·IP·내부 도메인, 운영 관리 화면 URL
- 실제 사용자 이름, 이메일, 전화번호, 주소, 계정 ID 등 개인정보
- 실제 고객 로그, trace 원문과 운영 데이터

샘플은 `example.invalid`, `<PID>`와 `tenant-synthetic-*` 같은 명백한 합성 placeholder만 사용한다. placeholder에 실제 값을 복사하도록 요구하지 않으며, 비밀값 확인이 필요한 절차 대신 연결 가능 여부와 비민감 지표를 확인한다. 문서 저장 전 금지 패턴 검사와 사람 검토를 모두 수행하는 것이 후속 구현의 완료 조건이다.

## 7. 실제 참조 문서 표시 기준

AI 응답의 `references`에는 다음 조건을 모두 만족한 문서만 표시한다.

1. 인증된 Tenant 조건과 `ACTIVE` 최신 version 검증을 통과했다.
2. 검색 후보에만 머물지 않고 실제 LLM context에 포함됐다.
3. 최종 응답의 원인 후보, 확인 절차 또는 완화 설명이 해당 문서 내용을 근거로 사용했다.
4. 응답 저장·조회 시에도 동일 Tenant 접근 권한을 다시 확인했다.

표시 필드는 `documentId`, `title`, `version`으로 제한한다. `tenantId`, `ownerId`, 내부 저장 경로, embedding과 유사도 원점수는 사용자에게 노출하지 않는다. 사용하지 않은 문서, 권한이 사라진 문서, `SUPERSEDED` 또는 `RETIRED` 문서는 표시하지 않는다. 근거 문서가 없으면 빈 목록을 반환하고 RAG 근거가 있는 것처럼 표현하지 않는다.

## 8. 작성·검토 체크리스트

- [ ] 필수 metadata와 본문 7개 섹션이 모두 있다.
- [ ] `tenantId`와 `ownerId`의 역할이 구분된다.
- [ ] `version`이 기존 revision보다 크고 활성 문서가 하나뿐이다.
- [ ] 실제 운영 정보, 계정, secret과 개인정보가 없다.
- [ ] 확인 절차가 변경 작업보다 앞서고 명령은 읽기 전용 단일 진단이다.
- [ ] 완화 작업은 자동 실행을 전제하지 않고 승인 필요성을 표시한다.
- [ ] 폐기되거나 검토 기한이 지난 내용을 현재 절차로 표현하지 않는다.
