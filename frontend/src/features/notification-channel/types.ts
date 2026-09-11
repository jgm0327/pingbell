// Mirrors com.monit.pingbell.notification.type.NotificationChannelType.
export type NotificationChannelType = 'EMAIL' | 'SLACK' | 'DISCORD'

// Mirrors com.monit.pingbell.notification.dto.NotificationChannelResponse.
export interface NotificationChannel {
  publicId: string
  type: NotificationChannelType
  maskedTarget: string
  enabled: boolean
  createdAt: string
  updatedAt: string
}

// Mirrors com.monit.pingbell.notification.dto.NotificationChannelCreateRequest.
export interface NotificationChannelCreateInput {
  type: NotificationChannelType
  target: string
}

// Mirrors com.monit.pingbell.notification.dto.NotificationChannelUpdateRequest. Update also
// re-enables the channel server-side (NotificationChannelService.updateChannel calls
// channel.enable()) - there's no separate activate endpoint.
export interface NotificationChannelUpdateInput {
  target: string
}

// Mirrors com.monit.pingbell.notification.dto.NotificationChannelTestSendResponse.
export interface NotificationChannelTestSendResult {
  publicId: string
  type: NotificationChannelType
  success: boolean
  message: string
  testedAt: string
}
