import { StatusBadge } from '../../shared/components/StatusBadge'
import { checkStatusMeta } from './checkStatusMeta'
import type { CheckResult } from './types'

interface CheckResultTableProps {
  results: CheckResult[]
}

export function CheckResultTable({ results }: CheckResultTableProps) {
  return (
    <div className="overflow-x-auto rounded-lg border border-slate-200 bg-white">
      <table className="w-full min-w-[560px] text-left text-sm">
        <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase text-slate-500">
          <tr>
            <th className="px-4 py-2">체크 시각</th>
            <th className="px-4 py-2">상태</th>
            <th className="px-4 py-2">HTTP 상태</th>
            <th className="px-4 py-2">응답 시간</th>
            <th className="px-4 py-2">에러 메시지</th>
          </tr>
        </thead>
        <tbody>
          {results.map((result) => {
            const meta = checkStatusMeta(result.status)
            return (
              <tr key={result.id} className="border-b border-slate-100 last:border-0">
                <td className="px-4 py-2 text-slate-500">{new Date(result.checkedAt).toLocaleString()}</td>
                <td className="px-4 py-2">
                  <StatusBadge label={meta.label} tone={meta.tone} />
                </td>
                <td className="px-4 py-2 text-slate-500">{result.httpStatus ?? '-'}</td>
                <td className="px-4 py-2 text-slate-500">
                  {result.responseTimeMs !== null ? `${result.responseTimeMs}ms` : '-'}
                </td>
                <td className="max-w-xs truncate px-4 py-2 text-slate-500">{result.errorMessage ?? '-'}</td>
              </tr>
            )
          })}
        </tbody>
      </table>
    </div>
  )
}
