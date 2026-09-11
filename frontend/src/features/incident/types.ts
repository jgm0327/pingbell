import type { LogAnalysisResult } from '../log-analysis/types'

// Mirrors com.monit.pingbell.incident.domain.IncidentStatus.
export type IncidentStatus = 'OPEN' | 'RESOLVED'

// Mirrors com.monit.pingbell.incident.dto.IncidentResponse.
export interface Incident {
  id: number
  monitorId: number
  monitorName: string
  status: IncidentStatus
  startedAt: string
  resolvedAt: string | null
  lastErrorMessage: string | null
  createdAt: string
  updatedAt: string
}

// Mirrors com.monit.pingbell.incident.domain.IncidentLogAnalysisStatus.
export type IncidentLogAnalysisStatus = 'PROCESSING' | 'COMPLETED' | 'FAILED'

// Mirrors com.monit.pingbell.incident.dto.IncidentLogAnalysisResponse. `result` is populated only
// when status is COMPLETED; `errorMessage` only when status is FAILED.
export interface IncidentLogAnalysis {
  incidentId: number
  status: IncidentLogAnalysisStatus
  result: LogAnalysisResult | null
  errorMessage: string | null
  requestedAt: string
  completedAt: string | null
}
