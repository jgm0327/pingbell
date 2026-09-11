import type { StatusTone } from '../../shared/components/StatusBadge'
import type { NotificationEventType, NotificationFailureType, NotificationStatus } from './types'

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

// UI labels/tones for NotificationFailureType - only meaningful when status is FAILED (backend
// rejects failureType with any other status, see NotificationHistoryQueryService). RETRY_EXHAUSTED
// gets 'danger' since it will never be retried automatically again, unlike the other two.
const NOTIFICATION_FAILURE_TYPE_META: Record<NotificationFailureType, { label: string; tone: StatusTone }> = {
  CHANNEL_DISABLED: { label: '채널 비활성', tone: 'neutral' },
  SEND_FAILED: { label: '발송 실패', tone: 'warning' },
  RETRY_EXHAUSTED: { label: '재시도 초과', tone: 'danger' },
}

export function notificationFailureTypeMeta(
  failureType: NotificationFailureType,
): { label: string; tone: StatusTone } {
  return NOTIFICATION_FAILURE_TYPE_META[failureType]
}

const NOTIFICATION_EVENT_TYPE_LABEL: Record<NotificationEventType, string> = {
  INCIDENT_OPEN: '장애 발생',
  INCIDENT_RESOLVED: '복구 완료',
}

export function notificationEventTypeLabel(type: NotificationEventType): string {
  return NOTIFICATION_EVENT_TYPE_LABEL[type]
}
