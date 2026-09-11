import { useState } from 'react'
import { PageHeader } from '../shared/components/PageHeader'
import { LoadingSpinner } from '../shared/components/LoadingSpinner'
import { ErrorState } from '../shared/components/ErrorState'
import { EmptyState } from '../shared/components/EmptyState'
import { IncidentCard } from '../features/incident/IncidentCard'
import { useIncidents } from '../features/incident/queries'
import { ApiError } from '../shared/api/httpClient'
import type { IncidentStatus } from '../features/incident/types'

const FILTERS: { value: IncidentStatus | undefined; label: string }[] = [
  { value: undefined, label: '전체' },
  { value: 'OPEN', label: '장애 발생 중' },
  { value: 'RESOLVED', label: '복구 완료' },
]

export function IncidentListPage() {
  const [statusFilter, setStatusFilter] = useState<IncidentStatus | undefined>(undefined)
  const incidentsQuery = useIncidents(statusFilter)

  return (
    <div>
      <PageHeader title="장애 이력" description="Monitor에서 발생한 장애와 복구 이력을 확인합니다." />

      <div className="mb-4 flex gap-2">
        {FILTERS.map((filter) => (
          <button
            key={filter.label}
            type="button"
            onClick={() => setStatusFilter(filter.value)}
            className={`rounded-md px-3 py-1.5 text-sm font-medium ${
              statusFilter === filter.value
                ? 'bg-sky-100 text-sky-700'
                : 'text-slate-600 hover:bg-slate-100 hover:text-slate-900'
            }`}
          >
            {filter.label}
          </button>
        ))}
      </div>

      {incidentsQuery.isLoading && <LoadingSpinner label="장애 이력을 불러오는 중..." />}

      {incidentsQuery.isError && (
        <ErrorState
          message={
            incidentsQuery.error instanceof ApiError ? incidentsQuery.error.message : '장애 이력을 불러오지 못했습니다.'
          }
          onRetry={() => incidentsQuery.refetch()}
        />
      )}

      {incidentsQuery.isSuccess && incidentsQuery.data.length === 0 && (
        <EmptyState title="해당하는 장애 이력이 없습니다." description="조건에 맞는 Incident가 아직 없습니다." />
      )}

      {incidentsQuery.isSuccess && incidentsQuery.data.length > 0 && (
        <div className="space-y-3">
          {incidentsQuery.data.map((incident) => (
            <IncidentCard key={incident.id} incident={incident} />
          ))}
        </div>
      )}
    </div>
  )
}
