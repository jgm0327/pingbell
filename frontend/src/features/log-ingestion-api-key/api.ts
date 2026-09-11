import { httpClient } from '../../shared/api/httpClient'
import type { LogIngestionApiKey, LogIngestionApiKeyIssueResult } from './types'

export function getApiKeys(monitorId: number): Promise<LogIngestionApiKey[]> {
  return httpClient.get<LogIngestionApiKey[]>(`/api/monitors/${monitorId}/api-keys`).then((r) => r.data)
}

export function issueApiKey(monitorId: number): Promise<LogIngestionApiKeyIssueResult> {
  return httpClient.post<LogIngestionApiKeyIssueResult>(`/api/monitors/${monitorId}/api-keys`).then((r) => r.data)
}

export function revokeApiKey(monitorId: number, keyId: number): Promise<void> {
  return httpClient.delete(`/api/monitors/${monitorId}/api-keys/${keyId}`).then(() => undefined)
}
