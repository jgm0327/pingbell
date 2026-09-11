// Mirrors com.monit.pingbell.check.domain.CheckStatus.
export type CheckStatus = 'SUCCESS' | 'FAILURE' | 'TIMEOUT' | 'HTTP_ERROR' | 'SLOW_RESPONSE'

// Mirrors com.monit.pingbell.check.dto.CheckResultResponse.
export interface CheckResult {
  id: number
  monitorId: number
  status: CheckStatus
  httpStatus: number | null
  responseTimeMs: number | null
  errorMessage: string | null
  checkedAt: string
}
