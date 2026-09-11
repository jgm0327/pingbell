import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { PageHeader } from '../shared/components/PageHeader'
import { LoadingSpinner } from '../shared/components/LoadingSpinner'
import { ErrorState } from '../shared/components/ErrorState'
import { EmptyState } from '../shared/components/EmptyState'
import { StatusBadge } from '../shared/components/StatusBadge'
import { ConfirmModal } from '../shared/components/ConfirmModal'
import { monitorStatusMeta } from '../features/monitor/monitorStatusMeta'
import { useActivateMonitor, useDeleteMonitor, useMonitors, usePauseMonitor } from '../features/monitor/queries'
import { ApiError } from '../shared/api/httpClient'
import type { Monitor } from '../features/monitor/types'

export function MonitorListPage() {
  const navigate = useNavigate()
  const monitorsQuery = useMonitors()
  const pauseMonitor = usePauseMonitor()
  const activateMonitor = useActivateMonitor()
  const deleteMonitor = useDeleteMonitor()
  const [pendingDelete, setPendingDelete] = useState<Monitor | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)

  const toggleStatus = (monitor: Monitor) => {
    setActionError(null)
    const mutation = monitor.status === 'PAUSED' ? activateMonitor : pauseMonitor
    mutation.mutate(monitor.id, {
      onError: (error) => setActionError(error instanceof ApiError ? error.message : '상태 변경에 실패했습니다.'),
    })
  }

  const confirmDelete = () => {
    if (!pendingDelete) {
      return
    }
    setActionError(null)
    deleteMonitor.mutate(pendingDelete.id, {
      onSuccess: () => setPendingDelete(null),
      onError: (error) => {
        setActionError(error instanceof ApiError ? error.message : '삭제에 실패했습니다.')
        setPendingDelete(null)
      },
    })
  }

  return (
    <div>
      <PageHeader
        title="모니터"
        description="등록한 서버 URL의 헬스체크 상태를 확인합니다."
        action={
          <Link
            to="/monitors/new"
            className="rounded-md bg-sky-600 px-4 py-2 text-sm font-semibold text-white hover:bg-sky-700"
          >
            새 모니터 등록
          </Link>
        }
      />

      {actionError && (
        <p role="alert" className="mb-4 rounded-md bg-red-50 px-3 py-2 text-sm text-red-700">
          {actionError}
        </p>
      )}

      {monitorsQuery.isLoading && <LoadingSpinner label="모니터 목록을 불러오는 중..." />}

      {monitorsQuery.isError && (
        <ErrorState
          message={
            monitorsQuery.error instanceof ApiError
              ? monitorsQuery.error.message
              : '모니터 목록을 불러오지 못했습니다.'
          }
          onRetry={() => monitorsQuery.refetch()}
        />
      )}

      {monitorsQuery.isSuccess && monitorsQuery.data.length === 0 && (
        <EmptyState
          title="등록된 모니터가 없습니다."
          description="서버 URL을 등록하면 주기적으로 헬스체크를 시작합니다."
          action={
            <Link
              to="/monitors/new"
              className="rounded-md bg-sky-600 px-4 py-2 text-sm font-semibold text-white hover:bg-sky-700"
            >
              첫 모니터 등록하기
            </Link>
          }
        />
      )}

      {monitorsQuery.isSuccess && monitorsQuery.data.length > 0 && (
        <div className="overflow-x-auto rounded-lg border border-slate-200 bg-white">
          <table className="w-full min-w-[720px] text-left text-sm">
            <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase text-slate-500">
              <tr>
                <th className="px-4 py-3">이름</th>
                <th className="px-4 py-3">URL</th>
                <th className="px-4 py-3">상태</th>
                <th className="px-4 py-3">체크 주기</th>
                <th className="px-4 py-3 text-right">작업</th>
              </tr>
            </thead>
            <tbody>
              {monitorsQuery.data.map((monitor) => {
                const meta = monitorStatusMeta(monitor.status)
                const isToggleBusy =
                  (pauseMonitor.isPending && pauseMonitor.variables === monitor.id) ||
                  (activateMonitor.isPending && activateMonitor.variables === monitor.id)
                return (
                  <tr key={monitor.id} className="border-b border-slate-100 last:border-0 hover:bg-slate-50">
                    <td className="px-4 py-3 font-medium text-slate-800">
                      <Link to={`/monitors/${monitor.id}`} className="hover:underline">
                        {monitor.name}
                      </Link>
                    </td>
                    <td className="max-w-xs truncate px-4 py-3 text-slate-500">{monitor.url}</td>
                    <td className="px-4 py-3">
                      <StatusBadge label={meta.label} tone={meta.tone} />
                    </td>
                    <td className="px-4 py-3 text-slate-500">{monitor.intervalSeconds}초</td>
                    <td className="px-4 py-3">
                      <div className="flex justify-end gap-2">
                        <button
                          type="button"
                          onClick={() => toggleStatus(monitor)}
                          disabled={isToggleBusy}
                          className="rounded-md border border-slate-300 px-2.5 py-1 text-xs font-medium text-slate-700 hover:bg-slate-100 disabled:cursor-not-allowed disabled:opacity-60"
                        >
                          {monitor.status === 'PAUSED' ? '재활성화' : '일시정지'}
                        </button>
                        <button
                          type="button"
                          onClick={() => navigate(`/monitors/${monitor.id}/edit`)}
                          className="rounded-md border border-slate-300 px-2.5 py-1 text-xs font-medium text-slate-700 hover:bg-slate-100"
                        >
                          수정
                        </button>
                        <button
                          type="button"
                          onClick={() => setPendingDelete(monitor)}
                          className="rounded-md border border-red-200 px-2.5 py-1 text-xs font-medium text-red-600 hover:bg-red-50"
                        >
                          삭제
                        </button>
                      </div>
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      )}

      <ConfirmModal
        open={!!pendingDelete}
        title="모니터를 삭제할까요?"
        description={
          pendingDelete ? `"${pendingDelete.name}" 모니터를 삭제하면 더 이상 헬스체크가 수행되지 않습니다.` : undefined
        }
        confirmLabel="삭제"
        danger
        busy={deleteMonitor.isPending}
        onConfirm={confirmDelete}
        onCancel={() => setPendingDelete(null)}
      />
    </div>
  )
}
