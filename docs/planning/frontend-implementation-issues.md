# Pingbell 정식 Frontend 구현 이슈 분해

## 1. 현재 판단

이전에 실제로 쓰던 Frontend가 있었던 것으로 보이지만(README의 프로젝트 구조·API 함수 이름 등이 이미 구체적으로 적혀 있었음) 다른 로컬에서만 작업하고 푸시하지 않아 저장소에는 없는 상태였다. `frontend/`에 있던 RAG 테스트용 임시 하네스(머지 대상 아님)는 지우고, `docs/agents/frontend-agent.md` 기준으로 새로 시작한다.

기술 스택: Vite + React 19 + TypeScript, React Router, TanStack Query, React Hook Form, Tailwind CSS, axios. 전부 `docs/agents/frontend-agent.md`가 권장하는 조합이다.

## 2. 작업 분해와 우선순위

### Issue F1. 프로젝트 셋업 + 로그인/회원가입 — ✅ 완료 (2026-08-25)

- 목적: 나머지 모든 화면이 의존하는 기반(라우팅, 인증, 레이아웃)을 먼저 만든다.
- 구현:
  - Vite + React + TS 스캐폴드, `docs/agents/frontend-agent.md` 폴더 구조(`app/`, `shared/`, `features/`, `pages/`).
  - `shared/api/session.ts`: React 밖에서도 접근 가능한 세션 저장소(localStorage 백업), `shared/api/httpClient.ts`: axios 인스턴스 + 자동 토큰 첨부 + **인증 실패(403, `code` 없는 응답) 감지 시 자동 refresh-and-retry**. 이 백엔드가 인증 실패에 401이 아니라 403을 쓴다는 것과, refresh token이 매 갱신마다 회전(rotate)한다는 걸 이전 RAG 테스트 하네스 작업에서 확인한 내용을 그대로 반영했다.
  - `features/auth`: 로그인/회원가입 폼(React Hook Form, 접근성 있는 label/에러 메시지), `AuthContext`(`useSyncExternalStore`로 세션 반영).
  - `ProtectedRoute`: 미인증 시 `/login`으로 리다이렉트. 세션이 지워지면(로그아웃, refresh 실패) 현재 보호된 화면에서도 자동으로 반응해 리다이렉트된다.
  - `AppLayout`: 헤더 네비게이션(대시보드/모니터/장애 이력/알림 채널/설정) + 로그아웃 버튼.
  - F2~F6이 채울 화면은 전부 `ComingSoon` placeholder로 라우팅만 먼저 뚫어뒀다(`/dashboard`, `/monitors`, `/monitors/new`, `/monitors/:monitorId`, `/monitors/:monitorId/edit`, `/incidents`, `/notification-channels`).
  - `/settings`는 placeholder가 아니라 실제로 구현(로그인된 이메일 표시 + 로그아웃) — API 호출이 필요 없어 F1 범위에 포함.
- 완료 조건 충족: `npx tsc -b`/`npm run build` 통과. **실제 브라우저 + 실제 백엔드로 end-to-end 검증**: 회원가입(201) → 대시보드 리다이렉트 → 네비게이션 → 로그인(200, 별도 세션) → 미인증 상태로 보호된 경로 접근 시 `/login` 리다이렉트 → 로그아웃(204) → 세션 제거 후 자동 리다이렉트까지 전부 실제 API로 확인했다.
- 남겨둔 것: httpClient의 refresh-and-retry 경로는 F1 화면에 인증된 API 호출이 하나도 없어(모두 placeholder) 실제 트리거로 검증하지 못했다. F2에서 첫 실제 API 호출(Monitor 목록 조회)이 생기는 순간 자연스럽게 검증한다.

### Issue F2. 대시보드 + Monitor CRUD

- 우선순위: 2
- 범위: `GET /api/monitors`, `POST /api/monitors`, `GET /api/monitors/{id}`, `PATCH /api/monitors/{id}`, `PATCH .../pause`, `PATCH .../activate`, `DELETE /api/monitors/{id}`를 TanStack Query로 연동. 대시보드 요약 카드(전체/정상/장애 모니터 수).
- 의존성: F1.

### Issue F3. 체크결과 + 장애 이력 + 자동 로그분석 결과

- 우선순위: 3
- 범위: `GET /api/monitors/{id}/checks`, `GET /api/incidents`, `GET /api/monitors/{id}/incidents`, `GET /api/incidents/{id}/log-analysis`(자동 분석 결과 — PROCESSING/COMPLETED/FAILED 상태 표시).
- 의존성: F2.

### Issue F4. 알림 채널(이메일/Slack/Discord) + 이력

- 우선순위: 4
- 범위: `POST/GET/PATCH/DELETE /api/notification-channels`, `GET /api/notification-histories`, `POST .../resend`.
- 의존성: F2.

### Issue F5. 로그 분석(업로드) + RAG references

- 우선순위: 5
- 범위: `POST /api/v1/monitors/{id}/log-analyses` 업로드 화면, 응답의 `references`(Runbook 근거) 표시.
- 의존성: F2.

### Issue F6. Log Ingestion API Key 관리

- 우선순위: 6
- 범위: `POST/GET/DELETE /api/monitors/{id}/api-keys`, 발급 응답의 `fluentBitConfig`/`dockerComposeSnippet`를 복사 버튼으로 제공(원문 키처럼 발급 시 1회만 보여줌을 화면에 명확히 표시).
- 의존성: F2.

## 3. 공통 원칙

- Frontend가 임의로 백엔드에 없는 필드를 만들지 않는다 — 실제 DTO 기준으로만 타입을 만든다(`docs/agents/frontend-agent.md` §16).
- 색상만으로 상태를 표현하지 않는다, 로딩/에러/빈 상태를 항상 구현한다.
- API 호출 코드는 컴포넌트에 직접 쓰지 않고 feature별 `api.ts`로 분리한다.

## 4. 다음에 사용할 프롬프트

```text
AGENTS.md와 docs/agents/frontend-agent.md, docs/agents/backend-agent.md를 읽고 docs/planning/frontend-implementation-issues.md의 Issue F2(대시보드 + Monitor CRUD)만 진행해줘.

조건:
- GET/POST/PATCH/DELETE /api/monitors 전체를 TanStack Query로 연동한다.
- 로딩/에러/빈 상태를 전부 구현한다.
- 백엔드에 없는 필드를 임의로 만들지 않는다 — MonitorResponse/MonitorRegisterRequest 실제 필드만 쓴다.
- 색상만으로 정상/장애 상태를 구분하지 않는다.
```
