# Pingbell Frontend

Vite + React 19 + TypeScript. 진행 계획은 `docs/planning/frontend-implementation-issues.md` 참고
(Issue F1~F6 단위로 진행 중).

## 실행

```bash
npm install
npm run dev
```

`http://localhost:5173`에서 열린다 (포트가 이미 쓰이고 있으면 Vite가 자동으로 다음 포트를 쓴다).
`/api`, `/actuator` 요청은 `vite.config.ts`의 proxy 설정으로 `http://localhost:8080`(백엔드)에
전달된다 — 백엔드를 먼저 띄워야 한다 (`docker compose up -d` 또는 `.\gradlew.bat bootRun`).

## 구조

```text
src
├── app
│   ├── providers   # QueryClientProvider, BrowserRouter, AuthProvider
│   └── router       # 라우트 테이블
├── shared
│   ├── api          # httpClient(axios, 인증 refresh 포함), session, 공통 타입
│   ├── components    # ProtectedRoute, ComingSoon 등 재사용 컴포넌트
│   └── layout        # AppLayout(헤더/네비게이션)
├── features
│   └── auth          # 로그인/회원가입 폼, AuthContext
└── pages              # 라우트에 매핑되는 페이지 컴포넌트
```

기능별 API 호출/타입은 `features/<feature>/api.ts`, `features/<feature>/types.ts`에 둔다
(`docs/agents/frontend-agent.md` 폴더 구조 기준).

## 인증 처리 주의사항

이 백엔드는 **인증 실패에 401이 아니라 403을 반환**한다(커스텀 `AuthenticationEntryPoint`가
없어서 Spring Security 기본 동작을 그대로 씀). `shared/api/httpClient.ts`는 이걸 실제 앱 레벨
403(예: `MONITOR_ACCESS_DENIED`, 항상 `code` 필드 있음)과 구분해서, `code` 없는 403/401만
"인증 실패"로 보고 access token을 자동으로 갱신한 뒤 요청을 한 번 재시도한다. 재시도까지
실패하면 세션을 지우고 로그인 화면으로 보낸다.

Access token은 6분(`jwt.access-token-expiration-ms`)마다 만료되므로, 이 로직 없이는 6분마다
"로그인이 풀리는" 것처럼 보인다 — 실제로 겪었던 문제라 여기 남겨둔다.

## 검증

```bash
npx tsc -b       # 타입 체크
npm run build    # 프로덕션 빌드
```
