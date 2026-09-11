import { useState } from 'react'
import { LoadingSpinner } from '../../shared/components/LoadingSpinner'
import { ErrorState } from '../../shared/components/ErrorState'
import { EmptyState } from '../../shared/components/EmptyState'
import { StatusBadge } from '../../shared/components/StatusBadge'
import { ConfirmModal } from '../../shared/components/ConfirmModal'
import { CopyButton } from '../../shared/components/CopyButton'
import { ApiError } from '../../shared/api/httpClient'
import { useIssueLogIngestionApiKey, useLogIngestionApiKeys, useRevokeLogIngestionApiKey } from './queries'
import type { LogIngestionApiKeyIssueResult } from './types'

interface LogIngestionApiKeySectionProps {
  monitorId: number
}

export function LogIngestionApiKeySection({ monitorId }: LogIngestionApiKeySectionProps) {
  const keysQuery = useLogIngestionApiKeys(monitorId)
  const issueKey = useIssueLogIngestionApiKey(monitorId)
  const revokeKey = useRevokeLogIngestionApiKey(monitorId)

  const [issueError, setIssueError] = useState<string | null>(null)
  const [issued, setIssued] = useState<LogIngestionApiKeyIssueResult | null>(null)
  const [revokeTarget, setRevokeTarget] = useState<{ id: number; keyPrefix: string } | null>(null)
  const [revokeError, setRevokeError] = useState<string | null>(null)

  const handleIssue = () => {
    setIssueError(null)
    issueKey.mutate(undefined, {
      onSuccess: (result) => setIssued(result),
      onError: (error) => setIssueError(error instanceof ApiError ? error.message : 'API Key 발급에 실패했습니다.'),
    })
  }

  const confirmRevoke = () => {
    if (!revokeTarget) {
      return
    }
    setRevokeError(null)
    revokeKey.mutate(revokeTarget.id, {
      onSuccess: () => setRevokeTarget(null),
      onError: (error) => {
        setRevokeError(error instanceof ApiError ? error.message : '폐기에 실패했습니다.')
        setRevokeTarget(null)
      },
    })
  }

  return (
    <div className="rounded-lg border border-slate-200 bg-white p-4">
      <div className="mb-3 flex items-center justify-between">
        <h2 className="text-sm font-semibold text-slate-800">Log Ingestion API Key</h2>
        <button
          type="button"
          onClick={handleIssue}
          disabled={issueKey.isPending}
          className="rounded-md bg-sky-600 px-3 py-1.5 text-sm font-semibold text-white hover:bg-sky-700 disabled:cursor-not-allowed disabled:opacity-60"
        >
          {issueKey.isPending ? '발급 중…' : '새 API Key 발급'}
        </button>
      </div>

      {issueError && (
        <p role="alert" className="mb-3 rounded-md bg-red-50 px-3 py-2 text-sm text-red-700">
          {issueError}
        </p>
      )}

      {issued && (
        <div className="mb-4 space-y-3 rounded-md border border-amber-300 bg-amber-50 p-4">
          <p className="text-sm font-medium text-amber-900">
            이 API Key는 지금 한 번만 표시됩니다. 다시 보여줄 수 없으니 지금 복사해두세요.
          </p>

          <div>
            <div className="mb-1 flex items-center justify-between">
              <span className="text-xs font-semibold uppercase text-slate-500">API Key</span>
              <CopyButton value={issued.apiKey} />
            </div>
            <code className="block overflow-x-auto rounded bg-slate-900 px-2 py-1.5 text-xs text-slate-100">
              {issued.apiKey}
            </code>
          </div>

          <div>
            <div className="mb-1 flex items-center justify-between">
              <span className="text-xs font-semibold uppercase text-slate-500">fluent-bit.conf</span>
              <CopyButton value={issued.fluentBitConfig} />
            </div>
            <pre className="overflow-x-auto whitespace-pre rounded bg-slate-900 p-2 text-xs text-slate-100">
              {issued.fluentBitConfig}
            </pre>
          </div>

          <div>
            <div className="mb-1 flex items-center justify-between">
              <span className="text-xs font-semibold uppercase text-slate-500">docker-compose 스니펫</span>
              <CopyButton value={issued.dockerComposeSnippet} />
            </div>
            <pre className="overflow-x-auto whitespace-pre rounded bg-slate-900 p-2 text-xs text-slate-100">
              {issued.dockerComposeSnippet}
            </pre>
          </div>

          <button
            type="button"
            onClick={() => setIssued(null)}
            className="rounded-md border border-amber-300 px-3 py-1.5 text-xs font-medium text-amber-800 hover:bg-amber-100"
          >
            닫기
          </button>
        </div>
      )}

      {revokeError && (
        <p role="alert" className="mb-3 rounded-md bg-red-50 px-3 py-2 text-sm text-red-700">
          {revokeError}
        </p>
      )}

      {keysQuery.isLoading && <LoadingSpinner label="API Key 목록을 불러오는 중..." />}

      {keysQuery.isError && (
        <ErrorState
          message={keysQuery.error instanceof ApiError ? keysQuery.error.message : 'API Key 목록을 불러오지 못했습니다.'}
          onRetry={() => keysQuery.refetch()}
        />
      )}

      {keysQuery.isSuccess && keysQuery.data.length === 0 && (
        <EmptyState
          title="발급된 API Key가 없습니다."
          description="Fluent Bit 등 로그 수집기가 로그를 보내려면 API Key가 필요합니다."
        />
      )}

      {keysQuery.isSuccess && keysQuery.data.length > 0 && (
        <div className="overflow-x-auto rounded-lg border border-slate-200">
          <table className="w-full min-w-[520px] text-left text-sm">
            <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase text-slate-500">
              <tr>
                <th className="px-4 py-2">Key</th>
                <th className="px-4 py-2">발급일</th>
                <th className="px-4 py-2">마지막 사용</th>
                <th className="px-4 py-2">상태</th>
                <th className="px-4 py-2 text-right">작업</th>
              </tr>
            </thead>
            <tbody>
              {keysQuery.data.map((key) => (
                <tr key={key.id} className="border-b border-slate-100 last:border-0">
                  <td className="px-4 py-2 font-mono text-slate-800">{key.keyPrefix}…</td>
                  <td className="px-4 py-2 text-slate-500">{new Date(key.createdAt).toLocaleString()}</td>
                  <td className="px-4 py-2 text-slate-500">
                    {key.lastUsedAt ? new Date(key.lastUsedAt).toLocaleString() : '사용 이력 없음'}
                  </td>
                  <td className="px-4 py-2">
                    <StatusBadge label={key.revoked ? '폐기됨' : '사용 중'} tone={key.revoked ? 'neutral' : 'success'} />
                  </td>
                  <td className="px-4 py-2 text-right">
                    {!key.revoked && (
                      <button
                        type="button"
                        onClick={() => setRevokeTarget({ id: key.id, keyPrefix: key.keyPrefix })}
                        className="rounded-md border border-red-200 px-2.5 py-1 text-xs font-medium text-red-600 hover:bg-red-50"
                      >
                        폐기
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <ConfirmModal
        open={!!revokeTarget}
        title="API Key를 폐기할까요?"
        description={
          revokeTarget
            ? `"${revokeTarget.keyPrefix}…" 키를 폐기하면 이 키를 사용하는 로그 수집기가 더 이상 인증되지 않습니다. 되돌릴 수 없습니다.`
            : undefined
        }
        confirmLabel="폐기"
        danger
        busy={revokeKey.isPending}
        onConfirm={confirmRevoke}
        onCancel={() => setRevokeTarget(null)}
      />
    </div>
  )
}
