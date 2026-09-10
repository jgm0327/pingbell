// Mirrors com.monit.pingbell.monitor.domain.MonitorStatus.
export type MonitorStatus = 'ACTIVE' | 'PAUSED' | 'DOWN'

// Mirrors com.monit.pingbell.monitor.dto.MonitorResponse / MonitorRegisterResponse (identical shape).
export interface Monitor {
  id: number
  userId: number
  name: string
  url: string
  intervalSeconds: number
  timeoutMillis: number
  failureThreshold: number
  recoveryThreshold: number
  status: MonitorStatus
  nextCheckAt: string
}

// Mirrors com.monit.pingbell.monitor.dto.MonitorRegisterRequest / MonitorUpdateRequest (identical shape).
export interface MonitorInput {
  name: string
  url: string
  intervalSeconds: number
  timeoutMillis: number
  failureThreshold: number
  recoveryThreshold: number
}
