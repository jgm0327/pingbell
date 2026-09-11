import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import * as monitorApi from './api'
import type { MonitorInput } from './types'

export const monitorKeys = {
  all: ['monitors'] as const,
  detail: (monitorId: number) => ['monitors', monitorId] as const,
}

export function useMonitors() {
  return useQuery({ queryKey: monitorKeys.all, queryFn: monitorApi.getMonitors })
}

export function useMonitor(monitorId: number, enabled = true) {
  return useQuery({
    queryKey: monitorKeys.detail(monitorId),
    queryFn: () => monitorApi.getMonitorDetail(monitorId),
    enabled,
  })
}

function useInvalidateMonitors() {
  const queryClient = useQueryClient()
  return (monitorId?: number) => {
    queryClient.invalidateQueries({ queryKey: monitorKeys.all })
    if (monitorId !== undefined) {
      queryClient.invalidateQueries({ queryKey: monitorKeys.detail(monitorId) })
    }
  }
}

export function useCreateMonitor() {
  const invalidate = useInvalidateMonitors()
  return useMutation({
    mutationFn: (input: MonitorInput) => monitorApi.createMonitor(input),
    onSuccess: (monitor) => invalidate(monitor.id),
  })
}

export function useUpdateMonitor(monitorId: number) {
  const invalidate = useInvalidateMonitors()
  return useMutation({
    mutationFn: (input: MonitorInput) => monitorApi.updateMonitor(monitorId, input),
    onSuccess: () => invalidate(monitorId),
  })
}

export function usePauseMonitor() {
  const invalidate = useInvalidateMonitors()
  return useMutation({
    mutationFn: (monitorId: number) => monitorApi.pauseMonitor(monitorId),
    onSuccess: (_monitor, monitorId) => invalidate(monitorId),
  })
}

export function useActivateMonitor() {
  const invalidate = useInvalidateMonitors()
  return useMutation({
    mutationFn: (monitorId: number) => monitorApi.activateMonitor(monitorId),
    onSuccess: (_monitor, monitorId) => invalidate(monitorId),
  })
}

export function useDeleteMonitor() {
  const invalidate = useInvalidateMonitors()
  return useMutation({
    mutationFn: (monitorId: number) => monitorApi.deleteMonitor(monitorId),
    onSuccess: () => invalidate(),
  })
}
