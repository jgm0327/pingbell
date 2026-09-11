## 작업 내용

`docs/next_feature_request.md`의 "알림 발송 실패 이력에 실패 유형(failureType) 필터 노출"을 구현했다. 백엔드 `GET /api/notification-histories`가 이미 `?failureType=` 쿼리 파라미터를 지원하고 있어서(`NotificationHistoryController.getHistories`, `NotificationHistoryQueryService`), 백엔드 변경 없이 Frontend만으로 끝났다.

## 변경 사항

- **`features/notification-history/notificationHistoryMeta.ts`**: `notificationFailureTypeMeta` 추가 — `CHANNEL_DISABLED`/`SEND_FAILED`/`RETRY_EXHAUSTED`를 각각 `채널 비활성`(neutral)/`발송 실패`(warning)/`재시도 초과`(danger) 라벨+톤으로 매핑. `RETRY_EXHAUSTED`만 `danger`로 둔 건 더 이상 자동 재시도되지 않는 상태라서다.
- **`pages/NotificationChannelPage.tsx`**:
  - 상태 필터가 `실패`일 때만 `실패 유형`(전체/채널 비활성/발송 실패/재시도 초과) 하위 필터를 노출한다. 백엔드가 `failureType`을 `status=FAILED`와 함께일 때만 허용하므로(`NotificationHistoryQueryService.validateFailureType`), 상태 필터를 다른 값으로 바꾸면 `failureType`도 같이 초기화한다.
  - 각 이력 행의 에러 열에 `history.failureType`이 있으면 배지를 항상 표시한다 — 하위 필터를 안 켜도 재시도 초과/채널 비활성 이력이 한눈에 구분된다.

## API 계약

- 신규/변경 없음. 기존 `GET /api/notification-histories?status=FAILED&failureType={type}`을 프론트에서 처음 사용하기 시작했을 뿐이다.

## 테스트 결과

- [x] `npx tsc -b`, `npm run build` — 통과
- [x] **실제 브라우저로 end-to-end 확인**: 테스트 계정에서 `실패` 상태 필터 선택 → `실패 유형` 하위 필터 노출 확인 → `발송 실패` 선택 시 해당 이력만(배지 일치) 표시 → `재시도 초과` 선택 시 빈 상태(`해당하는 발송 이력이 없습니다.`)가 에러 없이 표시됨 → 상태 필터를 `전체`로 되돌리면 하위 필터가 사라지고 400 에러 없이 정상 조회됨을 확인. 콘솔 에러 없음.

## 수동 테스트 방법

```bash
cd frontend && npm run dev
```
알림 채널 페이지 하단 발송 이력에서 `실패` 상태 필터를 누른 뒤 `실패 유형` 하위 필터(전체/채널 비활성/발송 실패/재시도 초과)를 각각 눌러 목록이 바뀌는지, 다른 상태 필터로 돌아갔을 때 에러 없이 하위 필터가 사라지는지 확인.

## 고민한 점

- `failureType`은 백엔드가 `status=FAILED`와 함께일 때만 허용해서(그 외 조합은 400), 하위 필터를 상태 필터에 종속시켜 렌더링하고, 상태가 바뀌면 자동으로 초기화하도록 했다 — 그렇지 않으면 사용자가 "재시도 초과"를 선택한 채로 다른 상태 필터를 누르는 순간 400 에러가 날 수 있었다.
- 하위 필터로 좁히지 않아도 실패 유형을 볼 수 있게, 이력 행 자체에도 배지를 항상 표시했다 — 완료 조건의 "재시도 초과와 채널 비활성이 시각적으로 구분된다"를 필터를 켜지 않은 기본 상태에서도 만족시키기 위해서다.

## Frontend 영향

해당 없음(이 PR 자체가 Frontend).

## 관련 이슈

close #
