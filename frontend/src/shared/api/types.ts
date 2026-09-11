// Mirrors com.monit.pingbell.global.exception.ErrorResponse. Keep in sync with the real backend
// DTO, not the other way around (see docs/agents/frontend-agent.md §16).
export interface ErrorResponse {
  timestamp: string
  status: number
  code: string
  message: string
  path: string
}

// Mirrors com.monit.pingbell.global.common.PageResponse<T>.
export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  numberOfElements: number
  first: boolean
  last: boolean
}
