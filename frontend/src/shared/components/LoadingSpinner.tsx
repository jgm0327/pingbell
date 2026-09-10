interface LoadingSpinnerProps {
  label?: string
}

export function LoadingSpinner({ label = '불러오는 중...' }: LoadingSpinnerProps) {
  return (
    <div role="status" className="flex items-center justify-center gap-2 py-12 text-sm text-slate-500">
      <span
        aria-hidden="true"
        className="h-4 w-4 animate-spin rounded-full border-2 border-slate-300 border-t-sky-600"
      />
      <span>{label}</span>
    </div>
  )
}
