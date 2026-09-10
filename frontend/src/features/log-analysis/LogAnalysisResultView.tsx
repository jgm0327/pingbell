import { StatusBadge } from '../../shared/components/StatusBadge'
import { confidenceMeta } from './logAnalysisMeta'
import type { LogAnalysisResult } from './types'

interface LogAnalysisResultViewProps {
  result: LogAnalysisResult
}

/** Renders one LogAnalysisResponse - shared by the automatic per-Incident analysis panel
 * (features/incident/IncidentLogAnalysisPanel) and the manual upload flow (Issue F5), since both
 * ultimately return the exact same backend DTO. */
export function LogAnalysisResultView({ result }: LogAnalysisResultViewProps) {
  return (
    <div className="space-y-4">
      <p className="text-sm text-slate-800">{result.summary}</p>

      {result.suspectedCauses.length > 0 && (
        <section>
          <h4 className="mb-2 text-xs font-semibold uppercase text-slate-500">의심되는 원인</h4>
          <ul className="space-y-2">
            {result.suspectedCauses.map((cause, index) => {
              const meta = confidenceMeta(cause.confidence)
              return (
                <li key={index} className="rounded-md bg-slate-50 p-3 text-sm">
                  <div className="mb-1 flex items-center gap-2">
                    <span className="font-medium text-slate-800">{cause.title}</span>
                    <StatusBadge label={meta.label} tone={meta.tone} />
                  </div>
                  <p className="text-slate-600">{cause.reason}</p>
                </li>
              )
            })}
          </ul>
        </section>
      )}

      {result.recommendedActions.length > 0 && (
        <section>
          <h4 className="mb-2 text-xs font-semibold uppercase text-slate-500">권장 조치</h4>
          <ol className="space-y-2">
            {result.recommendedActions.map((action) => (
              <li key={action.priority} className="rounded-md bg-slate-50 p-3 text-sm">
                <p className="text-slate-800">
                  <span className="mr-2 font-semibold text-slate-500">{action.priority}.</span>
                  {action.action}
                </p>
                {action.command && (
                  <code className="mt-1 block rounded bg-slate-900 px-2 py-1 text-xs text-slate-100">
                    {action.command}
                  </code>
                )}
              </li>
            ))}
          </ol>
        </section>
      )}

      {result.evidence.length > 0 && (
        <section>
          <h4 className="mb-2 text-xs font-semibold uppercase text-slate-500">근거 로그</h4>
          <ul className="space-y-1">
            {result.evidence.map((line, index) => (
              <li key={index} className="rounded bg-slate-900 px-2 py-1 font-mono text-xs text-slate-100">
                {line}
              </li>
            ))}
          </ul>
        </section>
      )}

      {result.warnings.length > 0 && (
        <section>
          <h4 className="mb-2 text-xs font-semibold uppercase text-slate-500">주의 사항</h4>
          <ul className="space-y-1">
            {result.warnings.map((warning, index) => (
              <li key={index} className="rounded-md bg-amber-50 px-3 py-2 text-sm text-amber-800">
                {warning}
              </li>
            ))}
          </ul>
        </section>
      )}

      {result.references.length > 0 && (
        <section>
          <h4 className="mb-2 text-xs font-semibold uppercase text-slate-500">참고한 Runbook</h4>
          <ul className="flex flex-wrap gap-2">
            {result.references.map((reference) => (
              <li
                key={reference.documentId}
                className="rounded-full bg-sky-50 px-3 py-1 text-xs font-medium text-sky-700"
              >
                {reference.title} (v{reference.version})
              </li>
            ))}
          </ul>
        </section>
      )}

      <p className="text-xs text-slate-400">
        분석 대상 로그 {result.analyzedCharacters.toLocaleString()}자
        {result.truncated ? ' (일부 생략됨)' : ''} · 원본 {result.originalSizeBytes.toLocaleString()} bytes
      </p>
    </div>
  )
}
