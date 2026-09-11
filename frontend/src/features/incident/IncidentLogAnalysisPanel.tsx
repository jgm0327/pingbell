import { LoadingSpinner } from '../../shared/components/LoadingSpinner'
import { ErrorState } from '../../shared/components/ErrorState'
import { ApiError } from '../../shared/api/httpClient'
import { LogAnalysisResultView } from '../log-analysis/LogAnalysisResultView'
import { useIncidentLogAnalysis } from './queries'

interface IncidentLogAnalysisPanelProps {
  incidentId: number
}

/** Renders the automatic AI log analysis for one Incident (see
 * IncidentLogAnalysisTriggerService/OpenAiLogAnalysisClient on the backend). Mounted only while
 * its IncidentCard is expanded, so the request/poll only happens on demand. */
export function IncidentLogAnalysisPanel({ incidentId }: IncidentLogAnalysisPanelProps) {
  const analysisQuery = useIncidentLogAnalysis(incidentId, true)

  if (analysisQuery.isLoading) {
    return <LoadingSpinner label="자동 로그 분석 결과를 불러오는 중..." />
  }

  if (analysisQuery.isError) {
    const error = analysisQuery.error
    if (error instanceof ApiError && error.status === 404) {
      return (
        <p className="rounded-md border border-dashed border-slate-300 bg-white px-3 py-3 text-sm text-slate-500">
          이 장애에 대한 자동 로그 분석 결과가 없습니다. (수집된 로그가 없었거나 아직 실행되지 않았습니다.)
        </p>
      )
    }
    return (
      <ErrorState
        message={error instanceof ApiError ? error.message : '분석 결과를 불러오지 못했습니다.'}
        onRetry={() => analysisQuery.refetch()}
      />
    )
  }

  const analysis = analysisQuery.data
  if (!analysis) {
    // Defensive - isLoading/isError above should cover every reachable state.
    return <LoadingSpinner label="자동 로그 분석 결과를 불러오는 중..." />
  }

  if (analysis.status === 'PROCESSING') {
    return <LoadingSpinner label="AI가 로그를 분석하고 있습니다..." />
  }

  if (analysis.status === 'FAILED') {
    return (
      <ErrorState
        message={analysis.errorMessage ?? '자동 로그 분석에 실패했습니다.'}
        onRetry={() => analysisQuery.refetch()}
      />
    )
  }

  if (!analysis.result) {
    // Defensive - COMPLETED should always carry a result (see IncidentLogAnalysisResponse).
    return null
  }

  return (
    <div className="rounded-md border border-slate-200 bg-white p-4">
      <LogAnalysisResultView result={analysis.result} />
    </div>
  )
}
