import type { StatusTone } from '../../shared/components/StatusBadge'
import type { CheckStatus } from './types'

// UI labels/tones for CheckStatus (see docs/agents/frontend-agent.md §7).
const CHECK_STATUS_META: Record<CheckStatus, { label: string; tone: StatusTone }> = {
  SUCCESS: { label: '정상', tone: 'success' },
  FAILURE: { label: '실패', tone: 'danger' },
  TIMEOUT: { label: '시간 초과', tone: 'danger' },
  HTTP_ERROR: { label: 'HTTP 오류', tone: 'danger' },
  SLOW_RESPONSE: { label: '응답 지연', tone: 'warning' },
}

export function checkStatusMeta(status: CheckStatus): { label: string; tone: StatusTone } {
  return CHECK_STATUS_META[status]
}
