import { useState } from 'react'
import { PageHeader } from '../shared/components/PageHeader'
import { LoadingSpinner } from '../shared/components/LoadingSpinner'
import { ErrorState } from '../shared/components/ErrorState'
import { EmptyState } from '../shared/components/EmptyState'
import { StatusBadge } from '../shared/components/StatusBadge'
import { ApiError } from '../shared/api/httpClient'
import { NotificationChannelForm } from '../features/notification-channel/NotificationChannelForm'
import { NotificationChannelRow } from '../features/notification-channel/NotificationChannelRow'
import { useCreateNotificationChannel, useNotificationChannels } from '../features/notification-channel/queries'
import { notificationChannelTypeLabel } from '../features/notification-channel/notificationChannelMeta'
import { useNotificationHistories, useResendNotificationHistory } from '../features/notification-history/queries'
import {
  notificationEventTypeLabel,
  notificationStatusMeta,
} from '../features/notification-history/notificationHistoryMeta'
import type { NotificationChannelCreateInput } from '../features/notification-channel/types'
import type { NotificationStatus } from '../features/notification-history/types'

const HISTORY_PAGE_SIZE = 10

const STATUS_FILTERS: { value: NotificationStatus | undefined; label: string }[] = [
  { value: undefined, label: '전체' },
  { value: 'PENDING', label: '대기 중' },
  { value: 'RETRY_PENDING', label: '재시도 대기' },
  { value: 'SENT', label: '발송 완료' },
  { value: 'FAILED', label: '실패' },
]

const CHANNEL_FILTERS: { value: boolean | undefined; label: string }[] = [
  { value: undefined, label: '전체' },
  { value: true, label: '활성' },
  { value: false, label: '비활성' },
]

export function NotificationChannelPage() {
  const [channelFilter, setChannelFilter] = useState<boolean | undefined>(undefined)
  const channelsQuery = useNotificationChannels(channelFilter)
  const createChannel = useCreateNotificationChannel()
  const [createError, setCreateError] = useState<string | null>(null)

  const [historyPage, setHistoryPage] = useState(0)
  const [historyStatus, setHistoryStatus] = useState<NotificationStatus | undefined>(undefined)
  const historiesQuery = useNotificationHistories(historyPage, HISTORY_PAGE_SIZE, historyStatus)
  const resendHistory = useResendNotificationHistory()
  const [historyActionError, setHistoryActionError] = useState<string | null>(null)

  const handleCreate = (input: NotificationChannelCreateInput) => {
    setCreateError(null)
    createChannel.mutate(input, {
      onError: (error) => setCreateError(error instanceof ApiError ? error.message : '채널 추가에 실패했습니다.'),
    })
  }

  const handleResend = (historyId: number) => {
    setHistoryActionError(null)
    resendHistory.mutate(historyId, {
      onError: (error) => setHistoryActionError(error instanceof ApiError ? error.message : '재전송에 실패했습니다.'),
    })
  }

  return (
    <div>
      <PageHeader title="알림 채널" description="장애/복구 알림을 받을 채널을 관리하고 발송 이력을 확인합니다." />

      <div className="mb-4 rounded-lg border border-slate-200 bg-white p-4">
        <NotificationChannelForm busy={createChannel.isPending} errorMessage={createError} onSubmit={handleCreate} />
      </div>

      <div className="mb-3 flex gap-2">
        {CHANNEL_FILTERS.map((filter) => (
          <button
            key={filter.label}
            type="button"
            onClick={() => setChannelFilter(filter.value)}
            className={`rounded-md px-3 py-1.5 text-sm font-medium ${
              channelFilter === filter.value
                ? 'bg-sky-100 text-sky-700'
                : 'text-slate-600 hover:bg-slate-100 hover:text-slate-900'
            }`}
          >
            {filter.label}
          </button>
        ))}
      </div>

      {channelsQuery.isLoading && <LoadingSpinner label="알림 채널을 불러오는 중..." />}

      {channelsQuery.isError && (
        <ErrorState
          message={
            channelsQuery.error instanceof ApiError ? channelsQuery.error.message : '알림 채널을 불러오지 못했습니다.'
          }
          onRetry={() => channelsQuery.refetch()}
        />
      )}

      {channelsQuery.isSuccess && channelsQuery.data.length === 0 && channelFilter === undefined && (
        <EmptyState title="등록된 알림 채널이 없습니다." description="위에서 이메일, Slack, Discord 채널을 추가해보세요." />
      )}

      {channelsQuery.isSuccess && channelsQuery.data.length === 0 && channelFilter !== undefined && (
        <EmptyState
          title={channelFilter ? '활성 채널이 없습니다.' : '비활성 채널이 없습니다.'}
          description="다른 필터를 선택해보세요."
        />
      )}

      {channelsQuery.isSuccess && channelsQuery.data.length > 0 && (
        <div className="space-y-3">
          {channelsQuery.data.map((channel) => (
            <NotificationChannelRow key={channel.publicId} channel={channel} />
          ))}
        </div>
      )}

      <h2 className="mb-3 mt-8 text-sm font-semibold text-slate-800">알림 발송 이력</h2>

      <div className="mb-4 flex gap-2">
        {STATUS_FILTERS.map((filter) => (
          <button
            key={filter.label}
            type="button"
            onClick={() => {
              setHistoryStatus(filter.value)
              setHistoryPage(0)
            }}
            className={`rounded-md px-3 py-1.5 text-sm font-medium ${
              historyStatus === filter.value
                ? 'bg-sky-100 text-sky-700'
                : 'text-slate-600 hover:bg-slate-100 hover:text-slate-900'
            }`}
          >
            {filter.label}
          </button>
        ))}
      </div>

      {historyActionError && (
        <p role="alert" className="mb-4 rounded-md bg-red-50 px-3 py-2 text-sm text-red-700">
          {historyActionError}
        </p>
      )}

      {historiesQuery.isLoading && <LoadingSpinner label="발송 이력을 불러오는 중..." />}

      {historiesQuery.isError && (
        <ErrorState
          message={
            historiesQuery.error instanceof ApiError ? historiesQuery.error.message : '발송 이력을 불러오지 못했습니다.'
          }
          onRetry={() => historiesQuery.refetch()}
        />
      )}

      {historiesQuery.isSuccess && historiesQuery.data.content.length === 0 && (
        <EmptyState title="해당하는 발송 이력이 없습니다." />
      )}

      {historiesQuery.isSuccess && historiesQuery.data.content.length > 0 && (
        <>
          <div className="overflow-x-auto rounded-lg border border-slate-200 bg-white">
            <table className="w-full min-w-[800px] text-left text-sm">
              <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase text-slate-500">
                <tr>
                  <th className="px-4 py-2">발생 시각</th>
                  <th className="px-4 py-2">Monitor</th>
                  <th className="px-4 py-2">유형</th>
                  <th className="px-4 py-2">채널</th>
                  <th className="px-4 py-2">상태</th>
                  <th className="px-4 py-2">에러</th>
                  <th className="px-4 py-2 text-right">작업</th>
                </tr>
              </thead>
              <tbody>
                {historiesQuery.data.content.map((history) => {
                  const meta = notificationStatusMeta(history.status)
                  const isResending = resendHistory.isPending && resendHistory.variables === history.id
                  return (
                    <tr key={history.id} className="border-b border-slate-100 last:border-0">
                      <td className="px-4 py-2 text-slate-500">{new Date(history.createdAt).toLocaleString()}</td>
                      <td className="px-4 py-2 text-slate-800">{history.monitorName}</td>
                      <td className="px-4 py-2 text-slate-500">
                        {notificationEventTypeLabel(history.notificationType)}
                        {history.manualResend && <span className="ml-1 text-xs text-slate-400">(재전송)</span>}
                      </td>
                      <td className="px-4 py-2 text-slate-500">
                        {notificationChannelTypeLabel(history.channelType)} · {history.maskedTarget}
                      </td>
                      <td className="px-4 py-2">
                        <StatusBadge label={meta.label} tone={meta.tone} />
                      </td>
                      <td className="max-w-xs truncate px-4 py-2 text-slate-500">{history.errorMessage ?? '-'}</td>
                      <td className="px-4 py-2 text-right">
                        {history.status === 'FAILED' && (
                          <button
                            type="button"
                            onClick={() => handleResend(history.id)}
                            disabled={isResending}
                            className="rounded-md border border-slate-300 px-2.5 py-1 text-xs font-medium text-slate-700 hover:bg-slate-100 disabled:cursor-not-allowed disabled:opacity-60"
                          >
                            {isResending ? '재전송 중…' : '재전송'}
                          </button>
                        )}
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
          <div className="mt-3 flex items-center justify-between text-sm text-slate-500">
            <span>
              {historiesQuery.data.page + 1} / {historiesQuery.data.totalPages} 페이지 (총{' '}
              {historiesQuery.data.totalElements}건)
            </span>
            <div className="flex gap-2">
              <button
                type="button"
                onClick={() => setHistoryPage((page) => page - 1)}
                disabled={historiesQuery.data.first}
                className="rounded-md border border-slate-300 px-3 py-1 font-medium text-slate-700 hover:bg-slate-100 disabled:cursor-not-allowed disabled:opacity-60"
              >
                이전
              </button>
              <button
                type="button"
                onClick={() => setHistoryPage((page) => page + 1)}
                disabled={historiesQuery.data.last}
                className="rounded-md border border-slate-300 px-3 py-1 font-medium text-slate-700 hover:bg-slate-100 disabled:cursor-not-allowed disabled:opacity-60"
              >
                다음
              </button>
            </div>
          </div>
        </>
      )}
    </div>
  )
}
