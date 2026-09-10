import { httpClient } from '../../shared/api/httpClient'
import type { LogAnalysisResult } from './types'

export function analyzeLog(monitorId: number, file: File, question: string): Promise<LogAnalysisResult> {
  const formData = new FormData()
  formData.append('file', file)
  if (question.trim()) {
    formData.append('question', question.trim())
  }

  return httpClient
    .post<LogAnalysisResult>(`/api/v1/monitors/${monitorId}/log-analyses`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
    .then((r) => r.data)
}
