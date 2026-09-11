// Mirrors com.monit.pingbell.loganalysis.dto.Confidence.
export type Confidence = 'HIGH' | 'MEDIUM' | 'LOW'

// Mirrors com.monit.pingbell.loganalysis.dto.SuspectedCauseResponse.
export interface SuspectedCause {
  title: string
  confidence: Confidence
  reason: string
}

// Mirrors com.monit.pingbell.loganalysis.dto.RecommendedActionResponse.
export interface RecommendedAction {
  priority: number
  action: string
  command: string | null
}

// Mirrors com.monit.pingbell.loganalysis.dto.RunbookReferenceResponse.
export interface RunbookReference {
  documentId: string
  title: string
  version: number
}

// Mirrors com.monit.pingbell.loganalysis.dto.LogAnalysisResponse - the shared shape returned both
// by the manual upload endpoint (POST /api/v1/monitors/{id}/log-analyses) and, wrapped inside
// IncidentLogAnalysis, by the automatic per-incident analysis (see features/incident/types.ts).
export interface LogAnalysisResult {
  monitorId: number
  summary: string
  suspectedCauses: SuspectedCause[]
  recommendedActions: RecommendedAction[]
  evidence: string[]
  warnings: string[]
  references: RunbookReference[]
  truncated: boolean
  originalSizeBytes: number
  analyzedCharacters: number
}
