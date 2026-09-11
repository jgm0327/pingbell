interface MetricCardProps {
  label: string
  value: string | number
  tone?: 'default' | 'success' | 'danger'
}

const VALUE_TONE_CLASSES: Record<NonNullable<MetricCardProps['tone']>, string> = {
  default: 'text-slate-900',
  success: 'text-emerald-700',
  danger: 'text-red-700',
}

export function MetricCard({ label, value, tone = 'default' }: MetricCardProps) {
  return (
    <div className="rounded-lg border border-slate-200 bg-white p-4">
      <p className="text-sm text-slate-500">{label}</p>
      <p className={`mt-1 text-2xl font-bold ${VALUE_TONE_CLASSES[tone]}`}>{value}</p>
    </div>
  )
}
