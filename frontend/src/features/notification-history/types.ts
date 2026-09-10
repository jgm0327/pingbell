import type { NotificationChannelType } from '../notification-channel/types'

// Mirrors com.monit.pingbell.notification.type.NotificationType.
export type NotificationEventType = 'INCIDENT_OPEN' | 'INCIDENT_RESOLVED'

// Mirrors com.monit.pingbell.notification.type.NotificationStatus.
export type NotificationStatus = 'PENDING' | 'RETRY_PENDING' | 'SENT' | 'FAILED'

// Mirrors com.monit.pingbell.notification.type.NotificationFailureType.
export type NotificationFailureType = 'CHANNEL_DISABLED' | 'SEND_FAILED' | 'RETRY_EXHAUSTED'

// Mirrors com.monit.pingbell.notification.dto.NotificationHistoryResponse.
export interface NotificationHistory {
  id: number
  incidentId: number
  monitorId: number
  monitorName: string
  channelPublicId: string
  channelType: NotificationChannelType
  channelEnabled: boolean
  maskedTarget: string
  notificationType: NotificationEventType
  status: NotificationStatus
  retryCount: number
  maxRetryCount: number
  nextRetryAt: string | null
  lastAttemptedAt: string | null
  retryable: boolean
  sentAt: string | null
  manualResend: boolean
  resendOfHistoryId: number | null
  failureType: NotificationFailureType | null
  createdAt: string
  errorMessage: string | null
}
