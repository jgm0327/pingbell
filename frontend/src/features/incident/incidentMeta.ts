import type { StatusTone } from '../../shared/components/StatusBadge'
import type { IncidentStatus } from './types'

// UI labels/tones for IncidentStatus (see docs/agents/frontend-agent.md §7).
const INCIDENT_STATUS_META: Record<IncidentStatus, { label: string; tone: StatusTone }> = {
  OPEN: { label: '장애 발생 중', tone: 'danger' },
  RESOLVED: { label: '복구 완료', tone: 'success' },
}

export function incidentStatusMeta(status: IncidentStatus): { label: string; tone: StatusTone } {
  return INCIDENT_STATUS_META[status]
}
