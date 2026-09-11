import { httpClient } from '../../shared/api/httpClient'
import type {
  NotificationChannel,
  NotificationChannelCreateInput,
  NotificationChannelTestSendResult,
  NotificationChannelUpdateInput,
} from './types'

export function getChannels(enabled?: boolean): Promise<NotificationChannel[]> {
  return httpClient
    .get<NotificationChannel[]>('/api/notification-channels', { params: { enabled } })
    .then((r) => r.data)
}

export function createChannel(input: NotificationChannelCreateInput): Promise<NotificationChannel> {
  return httpClient.post<NotificationChannel>('/api/notification-channels', input).then((r) => r.data)
}

export function updateChannel(
  publicId: string,
  input: NotificationChannelUpdateInput,
): Promise<NotificationChannel> {
  return httpClient.patch<NotificationChannel>(`/api/notification-channels/${publicId}`, input).then((r) => r.data)
}

// Backend semantics: this disables the channel (soft), it does not delete the row.
export function disableChannel(publicId: string): Promise<void> {
  return httpClient.delete(`/api/notification-channels/${publicId}`).then(() => undefined)
}

export function testSendChannel(publicId: string): Promise<NotificationChannelTestSendResult> {
  return httpClient
    .post<NotificationChannelTestSendResult>(`/api/notification-channels/${publicId}/test-send`)
    .then((r) => r.data)
}
