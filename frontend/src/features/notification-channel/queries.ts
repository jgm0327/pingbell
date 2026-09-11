import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import * as channelApi from './api'
import type { NotificationChannelCreateInput, NotificationChannelUpdateInput } from './types'

const CHANNELS_KEY = ['notification-channels'] as const

export function useNotificationChannels(enabled?: boolean) {
  return useQuery({
    queryKey: [...CHANNELS_KEY, enabled],
    queryFn: () => channelApi.getChannels(enabled),
  })
}

export function useCreateNotificationChannel() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: NotificationChannelCreateInput) => channelApi.createChannel(input),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: CHANNELS_KEY }),
  })
}

export function useUpdateNotificationChannel(publicId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: NotificationChannelUpdateInput) => channelApi.updateChannel(publicId, input),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: CHANNELS_KEY }),
  })
}

export function useDisableNotificationChannel() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (publicId: string) => channelApi.disableChannel(publicId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: CHANNELS_KEY }),
  })
}

export function useTestSendNotificationChannel() {
  return useMutation({
    mutationFn: (publicId: string) => channelApi.testSendChannel(publicId),
  })
}
