## 작업 내용

정식 Frontend(`docs/planning/frontend-implementation-issues.md`)의 남은 Issue F2~F6을 전부 구현했다. F1(로그인/회원가입)만 있던 상태에서, 대시보드/Monitor CRUD/체크결과/장애이력/자동 로그분석/알림 채널/알림 이력/로그 분석 업로드/Log Ingestion API Key 관리까지 이어붙여서 계획된 화면이 모두 실구현으로 바뀌었다.

## 변경 사항

- **F2**: `features/monitor/*`, `pages/{DashboardPage,MonitorListPage,MonitorFormPage,MonitorDetailPage}.tsx`. Monitor CRUD 전체 연동 + 대시보드 요약 카드.
- **F3**: `features/check-result/*`, `features/incident/*`. 체크 결과 페이지네이션 테이블, 장애 이력 필터, Incident별 자동 로그분석 패널(PROCESSING 폴링).
- **F4**: `features/notification-channel/*`, `features/notification-history/*`, `pages/NotificationChannelPage.tsx`. 채널 CRUD(실제로는 soft-disable + 수정 시 재활성화) + 발송 이력 조회/재전송.
- **F5**: `features/log-analysis/*`. `.log/.txt` 업로드 + 선택적 질문 → AI 분석 결과 표시. `LogAnalysisResultView`를 F3의 자동 분석 패널과 공유.
- **F6**: `features/log-ingestion-api-key/*`. Monitor별 API Key 발급(1회성 원문 + fluent-bit.conf/docker-compose 스니펫 복사)·목록·폐기.
- 공용: `shared/components/{LoadingSpinner,ErrorState,EmptyState,StatusBadge,MetricCard,PageHeader,ConfirmModal,CopyButton}.tsx`, `shared/api/types.ts`(`PageResponse<T>` 추가), `shared/utils/duration.ts`.
- 버그 수정 2건 (아래 "고민한 점" 참고): `AppProviders.tsx`의 QueryClient `networkMode: 'always'`, `NotificationChannelRow`의 수정 폼 리셋.
- 이제 아무 곳에서도 안 쓰는 `shared/components/ComingSoon.tsx` 삭제.

## API 계약

- 신규 백엔드 변경 없음. 기존 엔드포인트만 사용: `/api/monitors/**`, `/api/monitors/{id}/checks`, `/api/incidents/**`, `/api/monitors/{id}/incidents`, `/api/notification-channels/**`, `/api/notification-histories/**`, `/api/v1/monitors/{id}/log-analyses`, `/api/monitors/{id}/api-keys/**`.
- 프론트 타입은 전부 실제 백엔드 DTO/enum을 코드로 직접 확인하고 그대로 미러링했다(`docs/agents/frontend-agent.md` §16).

## 테스트 결과

- [x] `npx tsc -b`
- [x] `npm run build`
- [x] **실제 Docker Compose 백엔드 + 실제 브라우저 end-to-end 확인**: Monitor CRUD, 체크 결과(실 데이터 715건) 페이지네이션, 장애 이력 필터, 알림 채널 CRUD + mailpit 실제 테스트 발송, 로그 업로드로 **실제 OpenAI 응답** 렌더링, API Key 발급/복사/폐기까지 전부 브라우저로 클릭해서 확인.

## 수동 테스트 방법

```bash
docker compose up -d          # 백엔드 :8080 (+ postgres/redis/kafka/mailpit)
cd frontend && npm install && npm run dev   # :5173
```

회원가입 → Monitor 등록(대상 URL은 `docker compose up -d mock-server` 후 `http://mock-server:4000/health` 추천) → 대시보드/모니터 목록/상세/장애 이력/알림 채널/로그 분석 업로드를 순서대로 눌러보면 된다.

## 고민한 점

- TanStack Query 기본 `networkMode: 'online'`이 (주로 백그라운드 tab 상태에서) 쿼리를 `fetchStatus: 'paused'`로 무기한 대기시키는 걸 발견했다. `navigator.onLine`은 `true`인데도 발생해서 `onlineManager`의 이벤트 기반 추적이 실제 상태와 어긋난 것으로 보인다. 이 앱은 자체 백엔드와만 통신하는 단일 오리진이라 오프라인 사전 차단이 의미 없다고 보고 `networkMode: 'always'`로 바꿨다.
- 알림 채널 인라인 수정 폼에서 `useForm`이 컴포넌트 레벨에 살아있어 폼 JSX가 unmount/remount돼도 리셋이 안 되는 버그를 발견 — 재오픈 시 `reset({ target: '' })`을 호출하도록 고쳤다.
- 로그 분석 결과(`LogAnalysisResponse`)를 자동 분석(Incident)과 수동 업로드(F5) 두 곳에서 똑같이 써야 해서, 렌더링 로직을 `features/log-analysis/LogAnalysisResultView.tsx`로 뽑아 공유했다.

## Frontend 영향

해당 없음(이 PR 자체가 Frontend).

## 관련 이슈

close #
