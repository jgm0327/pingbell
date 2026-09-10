## 작업 내용

`docs/next_feature_request.md`의 "알림 채널 목록 활성/비활성 필터"를 구현했다. 백엔드 `GET /api/notification-channels`가 이미 `?enabled=true/false` 쿼리 파라미터를 지원하고 있어서(`NotificationChannelController.getChannels`), 백엔드 변경 없이 Frontend만으로 끝났다.

## 변경 사항

- **`features/notification-channel/api.ts`**: `getChannels(enabled?: boolean)` — `enabled`를 axios `params`로 전달(값이 `undefined`면 axios가 자동으로 생략해 기존 "전체 조회" 동작과 동일).
- **`features/notification-channel/queries.ts`**: `useNotificationChannels(enabled?: boolean)` — query key에 `enabled`를 포함해 필터별로 캐시가 분리되게 했다. 기존 mutation들의 `invalidateQueries({ queryKey: ['notification-channels'] })`는 그대로 둬도 TanStack Query의 prefix 매칭으로 모든 필터 캐시가 함께 무효화된다.
- **`pages/NotificationChannelPage.tsx`**: `전체`/`활성`/`비활성` 필터 버튼 추가 — 알림 발송 이력의 기존 상태 필터(`STATUS_FILTERS`)와 동일한 버튼 그룹 스타일을 재사용했다. 필터링 결과가 비었을 때는 "등록된 채널이 없습니다"가 아니라 "활성/비활성 채널이 없습니다"로 문구를 구분했다.

## API 계약

- 신규/변경 없음. 기존 `GET /api/notification-channels?enabled={bool}`을 프론트에서 처음 사용하기 시작했을 뿐이다.

## 테스트 결과

- [x] `npx tsc -b`, `npm run build` — 통과
- [x] **실제 브라우저로 end-to-end 확인**: 기존 테스트 계정(Slack + 이메일 채널 보유)에서 Slack 채널을 비활성화 → `비활성` 필터에 Slack만 나타남, `활성` 필터에 이메일만 나타남, `전체` 필터에 둘 다 나타남을 스크린샷으로 직접 확인. 확인 후 Slack 채널을 재활성화해 계정을 원래 상태로 되돌렸다(원래 target 값은 마스킹되어 복구 불가능해 더미 웹훅 값으로 재활성화 — 이 채널은 애초에 `404 Not Found: no_team`으로 항상 실패하던 테스트용 값이라 실제 알림 동작에는 영향 없음).

## 수동 테스트 방법

```bash
cd frontend && npm run dev
```
알림 채널 페이지에서 채널 하나를 비활성화한 뒤 `전체`/`활성`/`비활성` 필터를 각각 눌러 목록이 바뀌는지 확인.

## 고민한 점

- 필터를 프론트 상태로만 두지 않고 실제로 백엔드 쿼리 파라미터(`enabled`)를 사용하도록 했다 — 이미 서버가 지원하는 기능이라 클라이언트 사이드 필터링보다 서버 쿼리를 쓰는 게 더 단순하고, 채널 수가 늘어나도 그대로 확장된다.
- 필터가 비었을 때의 EmptyState 문구를 "전체" 상태와 구분했다 — "등록된 채널이 없습니다"는 필터링 때문에 안 보이는 것과 진짜 채널이 하나도 없는 것을 헷갈리게 할 수 있어서다.

## Frontend 영향

해당 없음(이 PR 자체가 Frontend).

## 관련 이슈

close #
