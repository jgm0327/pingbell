import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { PageHeader } from '../shared/components/PageHeader'
import { LoadingSpinner } from '../shared/components/LoadingSpinner'
import { ErrorState } from '../shared/components/ErrorState'
import { EmptyState } from '../shared/components/EmptyState'
import { StatusBadge } from '../shared/components/StatusBadge'
import { ConfirmModal } from '../shared/components/ConfirmModal'
import { monitorStatusMeta } from '../features/monitor/monitorStatusMeta'
import { useActivateMonitor, useDeleteMonitor, useMonitor, usePauseMonitor } from '../features/monitor/queries'
import { CheckResultTable } from '../features/check-result/CheckResultTable'
import { useCheckResults } from '../features/check-result/queries'
import { IncidentCard } from '../features/incident/IncidentCard'
import { useMonitorIncidents } from '../features/incident/queries'
import { LogAnalysisUploadSection } from '../features/log-analysis/LogAnalysisUploadSection'
import { LogIngestionApiKeySection } from '../features/log-ingestion-api-key/LogIngestionApiKeySection'
import { ApiError } from '../shared/api/httpClient'

const CHECK_RESULTS_PAGE_SIZE = 10

export function MonitorDetailPage() {
  const { monitorId: monitorIdParam } = useParams()
  const monitorId = Number(monitorIdParam)
  const navigate = useNavigate()
  const monitorQuery = useMonitor(monitorId)
  const pauseMonitor = usePauseMonitor()
  const activateMonitor = useActivateMonitor()
  const deleteMonitor = useDeleteMonitor()
  const [confirmingDelete, setConfirmingDelete] = useState(false)
  const [actionError, setActionError] = useState<string | null>(null)
  const [checkPage, setCheckPage] = useState(0)
  const checkResultsQuery = useCheckResults(monitorId, checkPage, CHECK_RESULTS_PAGE_SIZE)
  const monitorIncidentsQuery = useMonitorIncidents(monitorId)

  if (monitorQuery.isLoading) {
    return <LoadingSpinner label="모니터 정보를 불러오는 중..." />
  }

  if (monitorQuery.isError || !monitorQuery.data) {
    return (
      <ErrorState
        message={
          monitorQuery.error instanceof ApiError ? monitorQuery.error.message : '모니터 정보를 불러오지 못했습니다.'
        }
        onRetry={() => monitorQuery.refetch()}
      />
    )
  }

  const monitor = monitorQuery.data
  const meta = monitorStatusMeta(monitor.status)
  const toggleMutation = monitor.status === 'PAUSED' ? activateMonitor : pauseMonitor

  const toggleStatus = () => {
    setActionError(null)
    toggleMutation.mutate(monitor.id, {
      onError: (error) => setActionError(error instanceof ApiError ? error.message : '상태 변경에 실패했습니다.'),
    })
  }

  const confirmDelete = () => {
    setActionError(null)
    deleteMonitor.mutate(monitor.id, {
      onSuccess: () => navigate('/monitors', { replace: true }),
      onError: (error) => {
        setActionError(error instanceof ApiError ? error.message : '삭제에 실패했습니다.')
        setConfirmingDelete(false)
      },
    })
  }

  return (
    <div>
      <PageHeader
        title={monitor.name}
        description={monitor.url}
        action={
          <div className="flex gap-2">
            <Link
              to={`/monitors/${monitor.id}/edit`}
              className="rounded-md border border-slate-300 px-3 py-1.5 text-sm font-medium text-slate-700 hover:bg-slate-100"
            >
              수정
            </Link>
            <button
              type="button"
              onClick={toggleStatus}
              disabled={toggleMutation.isPending}
              className="rounded-md border border-slate-300 px-3 py-1.5 text-sm font-medium text-slate-700 hover:bg-slate-100 disabled:cursor-not-allowed disabled:opacity-60"
            >
              {monitor.status === 'PAUSED' ? '재활성화' : '일시정지'}
            </button>
            <button
              type="button"
              onClick={() => setConfirmingDelete(true)}
              className="rounded-md border border-red-200 px-3 py-1.5 text-sm font-medium text-red-600 hover:bg-red-50"
            >
              삭제
            </button>
          </div>
        }
      />

      {actionError && (
        <p role="alert" className="mb-4 rounded-md bg-red-50 px-3 py-2 text-sm text-red-700">
          {actionError}
        </p>
      )}

      <div className="rounded-lg border border-slate-200 bg-white p-6">
        <div className="mb-4 flex items-center gap-2">
          <StatusBadge label={meta.label} tone={meta.tone} />
          <span className="text-sm text-slate-500">
            다음 체크 예정: {new Date(monitor.nextCheckAt).toLocaleString()}
          </span>
        </div>
        <dl className="grid grid-cols-2 gap-4 text-sm sm:grid-cols-4">
          <div>
            <dt className="text-slate-500">체크 주기</dt>
            <dd className="font-medium text-slate-800">{monitor.intervalSeconds}초</dd>
          </div>
          <div>
            <dt className="text-slate-500">Timeout</dt>
            <dd className="font-medium text-slate-800">{monitor.timeoutMillis}ms</dd>
          </div>
          <div>
            <dt className="text-slate-500">장애 판정 기준</dt>
            <dd className="font-medium text-slate-800">연속 실패 {monitor.failureThreshold}회</dd>
          </div>
          <div>
            <dt className="text-slate-500">복구 판정 기준</dt>
            <dd className="font-medium text-slate-800">연속 성공 {monitor.recoveryThreshold}회</dd>
          </div>
        </dl>
      </div>

      <div className="mt-6">
        <h2 className="mb-3 text-sm font-semibold text-slate-800">체크 결과</h2>

        {checkResultsQuery.isLoading && <LoadingSpinner label="체크 결과를 불러오는 중..." />}

        {checkResultsQuery.isError && (
          <ErrorState
            message={
              checkResultsQuery.error instanceof ApiError
                ? checkResultsQuery.error.message
                : '체크 결과를 불러오지 못했습니다.'
            }
            onRetry={() => checkResultsQuery.refetch()}
          />
        )}

        {checkResultsQuery.isSuccess && checkResultsQuery.data.content.length === 0 && (
          <EmptyState title="아직 체크 결과가 없습니다." description="다음 체크 주기가 되면 결과가 쌓이기 시작합니다." />
        )}

        {checkResultsQuery.isSuccess && checkResultsQuery.data.content.length > 0 && (
          <>
            <CheckResultTable results={checkResultsQuery.data.content} />
            <div className="mt-3 flex items-center justify-between text-sm text-slate-500">
              <span>
                {checkResultsQuery.data.page + 1} / {checkResultsQuery.data.totalPages} 페이지 (총{' '}
                {checkResultsQuery.data.totalElements}건)
              </span>
              <div className="flex gap-2">
                <button
                  type="button"
                  onClick={() => setCheckPage((page) => page - 1)}
                  disabled={checkResultsQuery.data.first}
                  className="rounded-md border border-slate-300 px-3 py-1 font-medium text-slate-700 hover:bg-slate-100 disabled:cursor-not-allowed disabled:opacity-60"
                >
                  이전
                </button>
                <button
                  type="button"
                  onClick={() => setCheckPage((page) => page + 1)}
                  disabled={checkResultsQuery.data.last}
                  className="rounded-md border border-slate-300 px-3 py-1 font-medium text-slate-700 hover:bg-slate-100 disabled:cursor-not-allowed disabled:opacity-60"
                >
                  다음
                </button>
              </div>
            </div>
          </>
        )}
      </div>

      <div className="mt-6">
        <h2 className="mb-3 text-sm font-semibold text-slate-800">장애 이력</h2>

        {monitorIncidentsQuery.isLoading && <LoadingSpinner label="장애 이력을 불러오는 중..." />}

        {monitorIncidentsQuery.isError && (
          <ErrorState
            message={
              monitorIncidentsQuery.error instanceof ApiError
                ? monitorIncidentsQuery.error.message
                : '장애 이력을 불러오지 못했습니다.'
            }
            onRetry={() => monitorIncidentsQuery.refetch()}
          />
        )}

        {monitorIncidentsQuery.isSuccess && monitorIncidentsQuery.data.length === 0 && (
          <EmptyState title="장애 이력이 없습니다." description="이 모니터에서 발생한 장애가 아직 없습니다." />
        )}

        {monitorIncidentsQuery.isSuccess && monitorIncidentsQuery.data.length > 0 && (
          <div className="space-y-3">
            {monitorIncidentsQuery.data.map((incident) => (
              <IncidentCard key={incident.id} incident={incident} showMonitorLink={false} />
            ))}
          </div>
        )}
      </div>

      <div className="mt-6">
        <LogAnalysisUploadSection monitorId={monitorId} />
      </div>

      <div className="mt-6">
        <LogIngestionApiKeySection monitorId={monitorId} />
      </div>

      <ConfirmModal
        open={confirmingDelete}
        title="모니터를 삭제할까요?"
        description={`"${monitor.name}" 모니터를 삭제하면 더 이상 헬스체크가 수행되지 않습니다.`}
        confirmLabel="삭제"
        danger
        busy={deleteMonitor.isPending}
        onConfirm={confirmDelete}
        onCancel={() => setConfirmingDelete(false)}
      />
    </div>
  )
}
