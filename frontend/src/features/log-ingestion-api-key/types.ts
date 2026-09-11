// Mirrors com.monit.pingbell.logingestion.dto.LogIngestionApiKeyResponse.
export interface LogIngestionApiKey {
  id: number
  keyPrefix: string
  createdAt: string
  lastUsedAt: string | null
  revoked: boolean
}

// Mirrors com.monit.pingbell.logingestion.dto.LogIngestionApiKeyIssueResponse. apiKey,
// fluentBitConfig and dockerComposeSnippet are only ever returned here, at issuance time - they
// can never be retrieved again afterward (see LogIngestionApiKeyService.issue).
export interface LogIngestionApiKeyIssueResult {
  id: number
  apiKey: string
  keyPrefix: string
  createdAt: string
  fluentBitConfig: string
  dockerComposeSnippet: string
}
