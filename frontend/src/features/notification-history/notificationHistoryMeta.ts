import type { StatusTone } from '../../shared/components/StatusBadge'
import type { NotificationEventType, NotificationStatus } from './types'

// UI labels/tones for NotificationStatus (see docs/agents/frontend-agent.md §7).
const NOTIFICATION_STATUS_META: Record<NotificationStatus, { label: string; tone: StatusTone }> = {
  PENDING: { label: '대기 중', tone: 'neutral' },
  RETRY_PENDING: { label: '재시도 대기', tone: 'warning' },
  SENT: { label: '발송 완료', tone: 'success' },
  FAILED: { label: '실패', tone: 'danger' },
}

export function notificationStatusMeta(status: NotificationStatus): { label: string; tone: StatusTone } {
  return NOTIFICATION_STATUS_META[status]
}

const NOTIFICATION_EVENT_TYPE_LABEL: Record<NotificationEventType, string> = {
  INCIDENT_OPEN: '장애 발생',
  INCIDENT_RESOLVED: '복구 완료',
}

export function notificationEventTypeLabel(type: NotificationEventType): string {
  return NOTIFICATION_EVENT_TYPE_LABEL[type]
}
