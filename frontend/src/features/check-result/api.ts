import { httpClient } from '../../shared/api/httpClient'
import type { PageResponse } from '../../shared/api/types'
import type { CheckResult } from './types'

export function getCheckResults(monitorId: number, page: number, size: number): Promise<PageResponse<CheckResult>> {
  return httpClient
    .get<PageResponse<CheckResult>>(`/api/monitors/${monitorId}/checks`, { params: { page, size } })
    .then((r) => r.data)
}
