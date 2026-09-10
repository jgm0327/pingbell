export type StatusTone = 'success' | 'danger' | 'warning' | 'neutral'

interface StatusBadgeProps {
  label: string
  tone: StatusTone
}

// Domain-agnostic on purpose - each feature (monitor, incident, ...) maps its own status enum to
// a {label, tone} pair (see features/monitor/monitorStatusMeta.ts) instead of this component
// knowing about any of them. Color is never the only signal - label text is always shown too
// (see docs/agents/frontend-agent.md §11).
const TONE_CLASSES: Record<StatusTone, string> = {
  success: 'bg-emerald-100 text-emerald-800',
  danger: 'bg-red-100 text-red-800',
  warning: 'bg-amber-100 text-amber-800',
  neutral: 'bg-slate-100 text-slate-700',
}

export function StatusBadge({ label, tone }: StatusBadgeProps) {
  return (
    <span className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium ${TONE_CLASSES[tone]}`}>
      {label}
    </span>
  )
}
