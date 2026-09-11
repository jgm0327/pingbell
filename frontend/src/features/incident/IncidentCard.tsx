import { useState } from 'react'
import { Link } from 'react-router-dom'
import { StatusBadge } from '../../shared/components/StatusBadge'
import { formatDuration } from '../../shared/utils/duration'
import { incidentStatusMeta } from './incidentMeta'
import { IncidentLogAnalysisPanel } from './IncidentLogAnalysisPanel'
import type { Incident } from './types'

interface IncidentCardProps {
  incident: Incident
  /** Show a link to the owning Monitor - omit on MonitorDetailPage, where it's already implied. */
  showMonitorLink?: boolean
}

export function IncidentCard({ incident, showMonitorLink = true }: IncidentCardProps) {
  const [expanded, setExpanded] = useState(false)
  const meta = incidentStatusMeta(incident.status)

  return (
    <div
      className={`rounded-lg border bg-white p-4 ${
        incident.status === 'OPEN' ? 'border-red-200' : 'border-slate-200'
      }`}
    >
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div>
          <div className="mb-1 flex items-center gap-2">
            <StatusBadge label={meta.label} tone={meta.tone} />
            {showMonitorLink && (
              <Link to={`/monitors/${incident.monitorId}`} className="text-sm font-medium text-slate-800 hover:underline">
                {incident.monitorName}
              </Link>
            )}
          </div>
          <p className="text-sm text-slate-500">
            시작: {new Date(incident.startedAt).toLocaleString()}
            {incident.resolvedAt && ` · 복구: ${new Date(incident.resolvedAt).toLocaleString()}`}
            {' · 지속 시간: '}
            {formatDuration(incident.startedAt, incident.resolvedAt)}
          </p>
          {incident.lastErrorMessage && (
            <p className="mt-1 max-w-xl truncate text-sm text-slate-600">{incident.lastErrorMessage}</p>
          )}
        </div>
        <button
          type="button"
          onClick={() => setExpanded((value) => !value)}
          className="rounded-md border border-slate-300 px-3 py-1.5 text-xs font-medium text-slate-700 hover:bg-slate-100"
        >
          {expanded ? '자동 로그 분석 접기' : '자동 로그 분석 보기'}
        </button>
      </div>

      {expanded && (
        <div className="mt-4">
          <IncidentLogAnalysisPanel incidentId={incident.id} />
        </div>
      )}
    </div>
  )
}
