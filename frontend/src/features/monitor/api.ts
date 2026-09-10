import { httpClient } from '../../shared/api/httpClient'
import type { Monitor, MonitorInput } from './types'

export function getMonitors(): Promise<Monitor[]> {
  return httpClient.get<Monitor[]>('/api/monitors').then((r) => r.data)
}

export function getMonitorDetail(monitorId: number): Promise<Monitor> {
  return httpClient.get<Monitor>(`/api/monitors/${monitorId}`).then((r) => r.data)
}

export function createMonitor(input: MonitorInput): Promise<Monitor> {
  return httpClient.post<Monitor>('/api/monitors', input).then((r) => r.data)
}

export function updateMonitor(monitorId: number, input: MonitorInput): Promise<Monitor> {
  return httpClient.patch<Monitor>(`/api/monitors/${monitorId}`, input).then((r) => r.data)
}

export function pauseMonitor(monitorId: number): Promise<Monitor> {
  return httpClient.patch<Monitor>(`/api/monitors/${monitorId}/pause`).then((r) => r.data)
}

export function activateMonitor(monitorId: number): Promise<Monitor> {
  return httpClient.patch<Monitor>(`/api/monitors/${monitorId}/activate`).then((r) => r.data)
}

export function deleteMonitor(monitorId: number): Promise<void> {
  return httpClient.delete(`/api/monitors/${monitorId}`).then(() => undefined)
}
