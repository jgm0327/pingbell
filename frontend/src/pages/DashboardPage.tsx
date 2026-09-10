import { Link } from 'react-router-dom'
import { PageHeader } from '../shared/components/PageHeader'
import { LoadingSpinner } from '../shared/components/LoadingSpinner'
import { ErrorState } from '../shared/components/ErrorState'
import { EmptyState } from '../shared/components/EmptyState'
import { MetricCard } from '../shared/components/MetricCard'
import { StatusBadge } from '../shared/components/StatusBadge'
import { monitorStatusMeta } from '../features/monitor/monitorStatusMeta'
import { useMonitors } from '../features/monitor/queries'
import { ApiError } from '../shared/api/httpClient'

export function DashboardPage() {
  const monitorsQuery = useMonitors()

  return (
    <div>
      <PageHeader title="대시보드" description="등록한 모니터의 상태를 한눈에 확인합니다." />

      {monitorsQuery.isLoading && <LoadingSpinner label="대시보드를 불러오는 중..." />}

      {monitorsQuery.isError && (
        <ErrorState
          message={
            monitorsQuery.error instanceof ApiError
              ? monitorsQuery.error.message
              : '대시보드 정보를 불러오지 못했습니다.'
          }
          onRetry={() => monitorsQuery.refetch()}
        />
      )}

      {monitorsQuery.isSuccess && monitorsQuery.data.length === 0 && (
        <EmptyState
          title="등록된 모니터가 없습니다."
          description="서버 URL을 등록하면 대시보드에 상태 요약이 표시됩니다."
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
        <>
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
            <MetricCard label="전체 모니터" value={monitorsQuery.data.length} />
            <MetricCard
              label="정상 모니터"
              value={monitorsQuery.data.filter((monitor) => monitor.status === 'ACTIVE').length}
              tone="success"
            />
            <MetricCard
              label="장애 모니터"
              value={monitorsQuery.data.filter((monitor) => monitor.status === 'DOWN').length}
              tone="danger"
            />
          </div>

          <div className="mt-6 rounded-lg border border-slate-200 bg-white">
            <div className="flex items-center justify-between border-b border-slate-200 px-4 py-3">
              <h2 className="text-sm font-semibold text-slate-800">모니터 목록</h2>
              <Link to="/monitors" className="text-sm font-medium text-sky-600 hover:underline">
                전체 보기
              </Link>
            </div>
            <ul className="divide-y divide-slate-100">
              {monitorsQuery.data.slice(0, 5).map((monitor) => {
                const meta = monitorStatusMeta(monitor.status)
                return (
                  <li key={monitor.id} className="flex items-center justify-between px-4 py-3">
                    <Link
                      to={`/monitors/${monitor.id}`}
                      className="text-sm font-medium text-slate-800 hover:underline"
                    >
                      {monitor.name}
                    </Link>
                    <StatusBadge label={meta.label} tone={meta.tone} />
                  </li>
                )
              })}
            </ul>
          </div>
        </>
      )}
    </div>
  )
}
