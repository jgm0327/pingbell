# Pingbell 로그 자동 수집 구현 이슈 분해

## 1. 현재 판단

로그 분석(`/api/v1/monitors/{monitorId}/log-analyses`)은 지금 완전 수동이다 — 장애가 나면 사람이 로그 파일을 찾아 직접 업로드해야 한다. 이 문서는 장애 발생 시 사람 개입 없이 최근 로그가 자동으로 분석에 붙도록 만드는 작업을 분해한다.

결정된 방향:

- **수집 방식은 push + 검증된 오픈소스 collector(Fluent Bit)** 로 한다. 여러 언어용 SDK를 직접 만들지 않는다 — 유지보수 부담이 큰 데 비해 실익이 적고, Fluent Bit/Vector.dev 같은 도구가 이미 배치·재시도·버퍼링을 검증된 방식으로 해결해준다.
- **목표는 "사고 발생 시 자동 분석"(아래 목표 1)을 1차로 한다.** 상시 로그 저장·검색(목표 2)은 별도 제품에 가까운 스코프라 지금 범위에 넣지 않는다. 영구 로그 저장소가 없어도 되므로 비용·보안 부담이 훨씬 작다.
- 사용자가 최소 1회 해야 하는 설정(피할 수 없음, Pingbell은 사용자 서버에 원격 접근 수단이 없음): ① Monitor별 API Key 발급, ② 그 서버에 Fluent Bit 설치 + Pingbell을 가리키는 설정 적용. 이 부담을 줄이기 위해 Key/URL이 이미 채워진 설정 파일을 Pingbell이 생성해준다 (Issue 4).

공통 원칙:

- 헬스체크·장애 판정 흐름은 절대 이 기능의 실패로 깨지지 않는다(`ai-agent.md`의 "AI 실패가 기존 흐름을 바꾸지 않는다" 원칙 재사용).
- 인증된 Monitor 소유권 경계를 모든 조회·수집 API에 적용한다.
- 원문 로그가 마스킹 전에 영구 저장소에 닿지 않는다.
- 필요 없는 데이터를 무제한 보관하지 않는다(짧은 TTL 버퍼만 사용).

## 2. 작업 분해와 우선순위

### Issue 1. Ingestion용 API Key 인증 — ✅ 완료 (2026-08-25)

- 목적: JWT(6분+refresh, 브라우저 세션 전제)와 별도로, 무인 프로세스가 계속 인증할 수 있는 장기·revocable 자격증명을 Monitor 단위로 만든다.
- 구현:
  - `LogIngestionApiKey` 엔티티 — `monitorId`, `tenantId`, `keyHash`(SHA-256, 원문 미저장), `keyPrefix`(표시용), `lastUsedAt`, `revokedAt`. Migration `V15__create_log_ingestion_api_keys.sql`.
  - `POST /api/monitors/{monitorId}/api-keys` — JWT 인증, 발급 시 원문 키(`pgbl_...`)를 1회만 응답.
  - `GET /api/monitors/{monitorId}/api-keys` — 목록 조회, `keyPrefix`/`lastUsedAt`/`revoked`만 노출.
  - `DELETE /api/monitors/{monitorId}/api-keys/{keyId}` — 즉시 폐기(idempotent).
  - `LogIngestionApiKeyAuthenticationFilter` — `Authorization: Bearer pgbl_...`를 JWT와 구분해 인증하고 `ROLE_INGESTION` 권한만 부여. `SecurityConfig`의 `anyRequest()`는 `ROLE_USER`만 허용하도록 조여서, 발급된 API Key로는 사람용 API를 하나도 호출할 수 없다.
  - `/api/v1/monitors/{monitorId}/logs`(Issue 2, 아직 미구현) 경로만 `ROLE_INGESTION`을 요구하도록 미리 예약해둠.
- 완료 조건 충족: 원문 키 재조회 불가, 다른 Tenant Monitor에 접근 불가(기존 `OwnershipInterceptor`가 `/api/monitors/{monitorId}/**` 전체에 이미 적용됨 — 이 신규 API도 자동으로 보호받음), revoke 즉시 반영, API Key로 `/api/monitors` 등 사람용 endpoint 호출 시 403.
- 테스트: `LogIngestionApiKeyServiceTest`(단위), `LogIngestionApiKeyAuthenticationFilterTest`(단위), `LogIngestionApiKeyControllerTest`(회원가입→Monitor 생성→발급/조회/폐기 전체 흐름, 교차 Tenant 차단, API Key로 사람용 API 호출 차단).

### Issue 2. 로그 ingestion endpoint — ✅ 완료 (2026-08-25)

- 목적: Fluent Bit(및 유사 collector)가 실제로 로그를 밀어넣을 수 있는 endpoint를 만든다.
- 구현:
  - `POST /api/v1/monitors/{monitorId}/logs`, `ROLE_INGESTION` 인증(Issue 1 필터가 예약해둔 경로).
  - `LogIngestionService`가 `Content-Type`으로 분기: `application/x-ndjson`(Fluent Bit `json_lines` 출력, 줄마다 JSON 객체에서 `log`→`message` 순으로 필드 추출, 파싱 실패 줄은 배치 전체를 실패시키지 않고 건너뜀) / `text/plain`(줄바꿈으로 split) / `Content-Type` 없음(text/plain으로 취급) / 그 외 타입은 415 거부.
  - `LogPreprocessor.mask()`를 `public`으로 변경해 재사용, 받은 줄마다 마스킹 후 빈 줄 제거.
  - `LogIngestionBufferService`: Redis List에 `RPUSH` + `LTRIM`으로 Monitor당 최근 `bufferMaxLines`(기본 500)줄만 유지, 매 append마다 TTL(`bufferTtlSeconds`, 기본 900초) 갱신. 영구 저장소 없음.
  - 요청 크기 상한(`maxRequestBytes`, 기본 512KB)과 분당 요청 수 제한(`maxRequestsPerMinute`, 기본 60, Redis 고정 윈도 카운터)을 `pingbell.log-ingestion.*`로 설정 가능하게 함.
  - API Key의 `principal.monitorId()`와 경로의 `{monitorId}`가 다르면 403(`LOG_INGESTION_MONITOR_MISMATCH`) — 한 Monitor용 Key로 다른 Monitor에 쓸 수 없다.
- 완료 조건 충족: 마스킹 전 원문이 디스크/DB는 물론 Redis에도 닿지 않는다(마스킹이 버퍼 append보다 먼저 실행됨). TTL 지나면 자동 소멸. 크기·빈도 초과 시 명확한 에러 코드로 거부.
- 제외 범위: 영구 저장, 검색 UI, 실시간 스트리밍 조회, Incident 자동 연결(Issue 3).
- 테스트: `LogIngestionServiceTest`(ndjson/text 파싱, 마스킹, 크기/모니터 불일치 거부, 단위), `LogIngestionBufferServiceTest`(Redis 호출 검증, 단위), `LogIngestionControllerTest`(회원가입→Monitor→API Key→실제 ingestion 호출까지 전체 흐름, JWT로는 거부됨, 다른 Monitor Key 거부, 크기 초과 거부, 분당 60회 초과 시 429).

### Issue 3. Incident 발생 시 자동 로그 분석 — ✅ 완료 (2026-08-25)

- 목적: 사람이 다시 로그를 찾아 업로드하지 않아도, 장애가 열리는 순간 최근 로그가 자동으로 분석된다.
- 구현:
  - `LogAnalysisService`를 리팩터링해 파일 업로드 경로와 공유하는 core(`analyzeContent`)를 뽑아내고, 버퍼 기반 자동 분석용 `analyzeBufferedContent(tenantId, monitorId, rawContent)`를 추가(질문 없음, Tenant 소유권 검증은 그대로 유지).
  - `IncidentLogAnalysisTriggerService`(`@Async("logAnalysisExecutor")`, `AsyncConfig`에 전용 bounded thread pool 추가): `IncidentDetectionService`가 Incident를 OPEN으로 만든 직후 호출. `LogIngestionBufferService.readAll(tenantId, monitorId)`이 비어 있으면 아무것도 하지 않고 조용히 반환.
  - `IncidentLogAnalysisResultService`: 별도 Spring bean으로 분리해 각 단계(processing 저장 → completed/failed 갱신)가 올바르게 프록시된 자체 트랜잭션에서 실행되도록 함(async 메서드 자체는 상속받은 트랜잭션이 없음). 결과는 `IncidentLogAnalysis` 엔티티(`V16` 마이그레이션)에 `LogAnalysisResponse`를 JSON으로 직렬화해 저장.
  - `GET /api/incidents/{incidentId}/log-analysis`: 분석이 없으면 404, 있으면 `status`(PROCESSING/COMPLETED/FAILED)와 완료 시 `result`(기존 `LogAnalysisResponse`와 동일 계약)를 반환.
  - `IncidentDetectionService.applyDetection`에서 OPEN 분기에만 트리거를 추가(RESOLVED에는 추가하지 않음), 기존 알림 발송과 같은 `try/catch`로 감싸 실패가 Incident 판정에 전혀 영향을 주지 않게 함.
- 발견해서 같이 고친 것: `WebConfig`의 `OwnershipInterceptor` 경로 등록이 `/api/monitors/{monitorId}/**`와 달리 `/api/incidents/{incidentId}`에는 `/**`가 빠져 있어서, 새 하위 endpoint가 자동으로 보호받지 못하는 기존 불일치를 발견해 `/api/incidents/{incidentId}/**`를 추가했다.
- 완료 조건 충족: 버퍼가 비어 있으면 아무 row도 만들지 않고 기존 수동 업로드 흐름은 전혀 건드리지 않는다. AI 분석 실패가 Incident 판정·알림 발송에 영향을 주지 않는다(단위 테스트로 예외가 트리거 서비스 밖으로 전파되지 않음을 확인). 자동 분석은 별도 스레드에서 실행되어 Incident 생성 응답을 지연시키지 않는다. 다른 Tenant의 Incident 조회로는 404(OwnershipInterceptor가 차단).
- 제외 범위: Incident 판정 로직 변경, 자동 복구, 실제 Kafka/HTTP end-to-end 트리거 테스트(단위 테스트로 트리거 호출과 결과 저장/조회를 각각 검증).
- 테스트: `IncidentLogAnalysisTriggerServiceTest`(버퍼 비었을 때 skip, 성공/실패 경로, 단위), `IncidentLogAnalysisResultServiceTest`(저장/갱신/직렬화/조회, 단위), `IncidentDetectionServiceTest`(OPEN 시 트리거 호출 검증, RESOLVED 시 미호출 검증 추가), `IncidentLogAnalysisControllerTest`(소유 Incident 조회, 분석 없을 때 404, 교차 Tenant 차단).

### Issue 4. 문서화 + Collector 설정 자동 생성 — ✅ 완료 (2026-08-25)

- 목적: 사용자가 채워야 하는 값을 "로그 파일 경로" 하나로 줄인다.
- 구현:
  - `LogIngestionConfigTemplateService`: Monitor ID/ingestion URL/API Key가 이미 채워진 `fluent-bit.conf`와 Docker sidecar `docker-compose` 스니펫을 생성. `pingbell.log-ingestion.public-base-url` 설정으로 host/port/tls를 계산.
  - **정적 문서가 아니라 API 응답으로 진행**: Issue 1의 `POST /api/monitors/{monitorId}/api-keys` 응답에 `fluentBitConfig`/`dockerComposeSnippet` 필드를 추가했다. 원문 키가 발급 시 1회만 노출되는 것과 같은 이유로, 이 두 값도 그 순간에만 만들어서 같이 보여준다(나중에 재발급 없이 재생성 불가).
  - `docker-compose` 스니펫에는 API Key를 넣지 않았다 — 그 값은 `.conf` 파일만 갖고, compose 파일은 그 파일을 마운트만 한다.
  - Docker 기반 배포에서 로그 파일 경로 자체를 몰라도 되는 `logging driver: fluentd` 패턴을 스니펫 안에 주석으로 함께 안내.
  - `docs/operations/log-ingestion-setup.md` 작성 + README에 짧은 안내 섹션 추가.
- 완료 조건 충족: 사용자가 편집해야 하는 값이 로그 경로(또는 Docker 볼륨 경로) 하나뿐이다.
- 실제 검증: 컨테이너로 띄운 실제 앱에서 API Key 발급 → 생성된 설정으로 실제 로그 전송 → mock-server 장애 발생 → Incident 자동 OPEN → 자동 로그 분석 COMPLETED(실제 OpenAI 응답, 한국어 원인 분석)까지 전체 파이프라인을 curl로 end-to-end 확인함(테스트가 아니라 실제 실행).
- 테스트: `LogIngestionConfigTemplateServiceTest`(host/port/tls 계산, 시크릿이 compose 스니펫에 안 들어가는지), `LogIngestionApiKeyServiceTest`/`LogIngestionApiKeyControllerTest`에 신규 필드 검증 추가.

## 3. 전체 완료 조건 — ✅ 4개 Issue 전부 완료 (2026-08-25)

- [x] API Key는 원문이 발급 시 1회만 노출되고, 이후 DB에서 복구 불가능하다.
- [x] Ingestion 경로는 `ROLE_INGESTION`만 허용하고, 사람용 API는 `ROLE_USER`만 허용한다 — 어느 쪽 자격증명도 반대편에서 쓸 수 없다.
- [x] 로그 원문은 마스킹 전에 영구 저장소에 닿지 않으며, 버퍼는 짧은 TTL로 자동 소멸한다.
- [x] 이 기능의 실패가 헬스체크·장애 판정·기존 수동 로그 업로드 흐름을 절대 깨지 않는다.
- [x] 사용자가 채워야 하는 값은 최소화되어 있고(로그 경로 하나), 그 사실이 문서에 명시되어 있다.

실제 컨테이너 환경에서 API Key 발급 → 생성된 설정으로 로그 전송 → Incident 자동 OPEN → 자동
로그 분석 COMPLETED까지 전체 파이프라인을 curl로 end-to-end 검증 완료.

추가 검증(2026-09-08): curl이 아니라 **실제 Fluent Bit 컨테이너**(`docker compose up -d
fluent-bit-test` - `docs/planning`의 Frontend F6에서 발급한 실제 API Key/설정 사용)로 배관까지
확인. `tail` input이 `sample-app.log`에 추가된 줄을 실시간으로 읽어 `http output`으로
`app:8080`에 전송, 서버가 `HTTP 202 {"acceptedLines":3}`로 응답하는 것과 Redis 버퍼
(`log-ingestion:buffer:{tenantId}:{monitorId}`)에 마스킹된 상태로 저장되는 것까지 실제 값으로
확인함(Bearer 토큰/이메일/IP가 각각 `[REDACTED]`로 치환됨). 다음 단계는 이 문서
범위 밖이며 `docs/next_feature_request.md`에서 관리한다.

## 4. 참고: 각 Issue 착수 시 사용했던 프롬프트

Issue 1~4는 순서대로 완료되었다. 비슷한 패턴의 다음 기능을 진행할 때 참고할 수 있도록, 마지막
Issue를 시작할 때 썼던 프롬프트 형태만 남겨둔다.

```text
AGENTS.md와 docs/agents/backend-agent.md를 읽고 docs/ai/log-ingestion-implementation-issues.md의 Issue 4(Collector 설정 자동 생성 + 문서화)만 진행해줘.

조건:
- Monitor ID/API Key/ingestion URL이 이미 채워진 fluent-bit.conf 템플릿과 Docker sidecar 스니펫을 생성한다.
- 사용자가 직접 채워야 하는 값은 로그 경로(또는 Docker 볼륨 경로) 하나로 줄인다.
- 정식 Frontend가 없으므로 API 응답 또는 정적 문서 중 하나로 방식을 정하고 명시한다.
- README 또는 별도 문서에 전체 설정 흐름을 정리한다.
```
