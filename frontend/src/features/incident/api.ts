import { httpClient } from '../../shared/api/httpClient'
import type { Incident, IncidentLogAnalysis, IncidentStatus } from './types'

export function getIncidents(status?: IncidentStatus): Promise<Incident[]> {
  return httpClient.get<Incident[]>('/api/incidents', { params: status ? { status } : undefined }).then((r) => r.data)
}

export function getMonitorIncidents(monitorId: number, status?: IncidentStatus): Promise<Incident[]> {
  return httpClient
    .get<Incident[]>(`/api/monitors/${monitorId}/incidents`, { params: status ? { status } : undefined })
    .then((r) => r.data)
}

export function getIncidentLogAnalysis(incidentId: number): Promise<IncidentLogAnalysis> {
  return httpClient.get<IncidentLogAnalysis>(`/api/incidents/${incidentId}/log-analysis`).then((r) => r.data)
}
