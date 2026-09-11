import type { StatusTone } from '../../shared/components/StatusBadge'
import type { MonitorStatus } from './types'

// UI labels/tones for MonitorStatus (see docs/agents/frontend-agent.md §7) - color alone never
// carries the meaning, every status also gets a Korean label.
const MONITOR_STATUS_META: Record<MonitorStatus, { label: string; tone: StatusTone }> = {
  ACTIVE: { label: '체크 중', tone: 'success' },
  PAUSED: { label: '일시 정지', tone: 'neutral' },
  DOWN: { label: '장애', tone: 'danger' },
}

export function monitorStatusMeta(status: MonitorStatus): { label: string; tone: StatusTone } {
  return MONITOR_STATUS_META[status]
}
