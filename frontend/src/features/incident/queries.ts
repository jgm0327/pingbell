import { useQuery } from '@tanstack/react-query'
import * as incidentApi from './api'
import type { IncidentStatus } from './types'

export function useIncidents(status?: IncidentStatus) {
  return useQuery({
    queryKey: ['incidents', { status }] as const,
    queryFn: () => incidentApi.getIncidents(status),
  })
}

export function useMonitorIncidents(monitorId: number, status?: IncidentStatus) {
  return useQuery({
    queryKey: ['monitors', monitorId, 'incidents', { status }] as const,
    queryFn: () => incidentApi.getMonitorIncidents(monitorId, status),
  })
}

export function useIncidentLogAnalysis(incidentId: number, enabled: boolean) {
  return useQuery({
    queryKey: ['incidents', incidentId, 'log-analysis'] as const,
    queryFn: () => incidentApi.getIncidentLogAnalysis(incidentId),
    enabled,
    // A PROCESSING analysis is actively running server-side (see IncidentLogAnalysisTriggerService) -
    // poll it every 3s until it lands on COMPLETED/FAILED, then stop.
    refetchInterval: (query) => (query.state.data?.status === 'PROCESSING' ? 3000 : false),
  })
}
