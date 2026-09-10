import { useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { PageHeader } from '../shared/components/PageHeader'
import { LoadingSpinner } from '../shared/components/LoadingSpinner'
import { ErrorState } from '../shared/components/ErrorState'
import { MonitorForm } from '../features/monitor/MonitorForm'
import { useCreateMonitor, useMonitor, useUpdateMonitor } from '../features/monitor/queries'
import { ApiError } from '../shared/api/httpClient'
import type { MonitorInput } from '../features/monitor/types'

export function MonitorFormPage() {
  const { monitorId: monitorIdParam } = useParams()
  const monitorId = monitorIdParam ? Number(monitorIdParam) : undefined
  const isEdit = monitorId !== undefined
  const navigate = useNavigate()
  const [formError, setFormError] = useState<string | null>(null)

  const monitorQuery = useMonitor(monitorId ?? 0, isEdit)
  const createMonitor = useCreateMonitor()
  const updateMonitor = useUpdateMonitor(monitorId ?? 0)

  if (isEdit && monitorQuery.isLoading) {
    return <LoadingSpinner label="모니터 정보를 불러오는 중..." />
  }

  if (isEdit && monitorQuery.isError) {
    return (
      <ErrorState
        message={
          monitorQuery.error instanceof ApiError ? monitorQuery.error.message : '모니터 정보를 불러오지 못했습니다.'
        }
        onRetry={() => monitorQuery.refetch()}
      />
    )
  }

  const handleSubmit = (input: MonitorInput) => {
    setFormError(null)
    if (isEdit) {
      updateMonitor.mutate(input, {
        onSuccess: (monitor) => navigate(`/monitors/${monitor.id}`),
        onError: (error) => setFormError(error instanceof ApiError ? error.message : '모니터 수정에 실패했습니다.'),
      })
    } else {
      createMonitor.mutate(input, {
        onSuccess: (monitor) => navigate(`/monitors/${monitor.id}`),
        onError: (error) => setFormError(error instanceof ApiError ? error.message : '모니터 등록에 실패했습니다.'),
      })
    }
  }

  const defaultValues =
    isEdit && monitorQuery.data
      ? {
          name: monitorQuery.data.name,
          url: monitorQuery.data.url,
          intervalSeconds: monitorQuery.data.intervalSeconds,
          timeoutMillis: monitorQuery.data.timeoutMillis,
          failureThreshold: monitorQuery.data.failureThreshold,
          recoveryThreshold: monitorQuery.data.recoveryThreshold,
        }
      : undefined

  return (
    <div>
      <PageHeader title={isEdit ? '모니터 수정' : '모니터 등록'} />
      <MonitorForm
        defaultValues={defaultValues}
        busy={createMonitor.isPending || updateMonitor.isPending}
        submitLabel={isEdit ? '수정' : '등록'}
        errorMessage={formError}
        onSubmit={handleSubmit}
        onCancel={() => navigate(-1)}
      />
    </div>
  )
}
