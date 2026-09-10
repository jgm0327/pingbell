import { httpClient } from '../../shared/api/httpClient'
import type { PageResponse } from '../../shared/api/types'
import type { NotificationFailureType, NotificationHistory, NotificationStatus } from './types'

export function getHistories(
  page: number,
  size: number,
  status?: NotificationStatus,
  failureType?: NotificationFailureType,
): Promise<PageResponse<NotificationHistory>> {
  return httpClient
    .get<PageResponse<NotificationHistory>>('/api/notification-histories', {
      params: { page, size, status, failureType },
    })
    .then((r) => r.data)
}

export function resendHistory(historyId: number): Promise<NotificationHistory> {
  return httpClient.post<NotificationHistory>(`/api/notification-histories/${historyId}/resend`).then((r) => r.data)
}
