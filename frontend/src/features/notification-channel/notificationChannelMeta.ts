import type { NotificationChannelType } from './types'

const NOTIFICATION_CHANNEL_TYPE_LABEL: Record<NotificationChannelType, string> = {
  EMAIL: '이메일',
  SLACK: 'Slack',
  DISCORD: 'Discord',
}

export function notificationChannelTypeLabel(type: NotificationChannelType): string {
  return NOTIFICATION_CHANNEL_TYPE_LABEL[type]
}
