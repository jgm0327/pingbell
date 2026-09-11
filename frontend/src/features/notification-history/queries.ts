import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import * as historyApi from './api'
import type { NotificationFailureType, NotificationStatus } from './types'

const HISTORIES_KEY = 'notification-histories'

export function useNotificationHistories(
  page: number,
  size: number,
  status?: NotificationStatus,
  failureType?: NotificationFailureType,
) {
  return useQuery({
    queryKey: [HISTORIES_KEY, { page, size, status, failureType }] as const,
    queryFn: () => historyApi.getHistories(page, size, status, failureType),
  })
}

export function useResendNotificationHistory() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (historyId: number) => historyApi.resendHistory(historyId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: [HISTORIES_KEY] }),
  })
}
