import { useRef, useState } from 'react'
import { LoadingSpinner } from '../../shared/components/LoadingSpinner'
import { ApiError } from '../../shared/api/httpClient'
import { useAnalyzeLog } from './queries'
import { LogAnalysisResultView } from './LogAnalysisResultView'

interface LogAnalysisUploadSectionProps {
  monitorId: number
}

/** Manual log analysis upload (Issue F5) - separate from the automatic per-Incident analysis
 * (features/incident), which runs on its own from the recent log buffer. This is for uploading
 * an arbitrary .log/.txt file on demand, with an optional question, and shows the exact same
 * result shape via LogAnalysisResultView. */
export function LogAnalysisUploadSection({ monitorId }: LogAnalysisUploadSectionProps) {
  const analyzeLog = useAnalyzeLog(monitorId)
  const fileInputRef = useRef<HTMLInputElement>(null)
  const [question, setQuestion] = useState('')
  const [fileError, setFileError] = useState<string | null>(null)

  const handleSubmit = (event: React.FormEvent) => {
    event.preventDefault()
    const file = fileInputRef.current?.files?.[0]
    if (!file) {
      setFileError('로그 파일을 선택해주세요.')
      return
    }
    setFileError(null)
    analyzeLog.mutate({ file, question })
  }

  const handleReset = () => {
    analyzeLog.reset()
    setQuestion('')
    if (fileInputRef.current) {
      fileInputRef.current.value = ''
    }
  }

  return (
    <div className="rounded-lg border border-slate-200 bg-white p-4">
      <h2 className="mb-3 text-sm font-semibold text-slate-800">로그 분석 업로드</h2>

      {!analyzeLog.isSuccess && (
        <form onSubmit={handleSubmit} noValidate className="flex flex-col gap-4">
          <div>
            <label htmlFor="log-file" className="block text-sm font-medium text-slate-700">
              로그 파일 (.log, .txt · 5MB 이하)
            </label>
            <input
              id="log-file"
              ref={fileInputRef}
              type="file"
              accept=".log,.txt,text/plain"
              aria-invalid={!!fileError}
              aria-describedby={fileError ? 'log-file-error' : undefined}
              className="mt-1 block w-full text-sm text-slate-700 file:mr-3 file:rounded-md file:border file:border-slate-300 file:bg-white file:px-3 file:py-1.5 file:text-sm file:font-medium file:text-slate-700 hover:file:bg-slate-100"
            />
            {fileError && (
              <p id="log-file-error" className="mt-1 text-sm text-red-600">
                {fileError}
              </p>
            )}
          </div>

          <div>
            <label htmlFor="log-question" className="block text-sm font-medium text-slate-700">
              추가 질문 (선택)
            </label>
            <textarea
              id="log-question"
              rows={2}
              value={question}
              onChange={(event) => setQuestion(event.target.value)}
              placeholder="예: 이 에러가 반복되는 이유가 뭘까요?"
              className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-sky-500 focus:outline-none focus:ring-1 focus:ring-sky-500"
            />
          </div>

          {analyzeLog.isError && (
            <p role="alert" className="text-sm text-red-600">
              {analyzeLog.error instanceof ApiError ? analyzeLog.error.message : '로그 분석에 실패했습니다.'}
            </p>
          )}

          <div>
            <button
              type="submit"
              disabled={analyzeLog.isPending}
              className="rounded-md bg-sky-600 px-4 py-2 text-sm font-semibold text-white hover:bg-sky-700 disabled:cursor-not-allowed disabled:opacity-60"
            >
              {analyzeLog.isPending ? '분석 중…' : '분석 요청'}
            </button>
          </div>
        </form>
      )}

      {analyzeLog.isPending && <LoadingSpinner label="AI가 로그를 분석하고 있습니다... (최대 30초 정도 걸릴 수 있습니다)" />}

      {analyzeLog.isSuccess && (
        <div>
          <div className="mb-3 flex items-center justify-between">
            <p className="text-sm text-slate-500">분석 결과</p>
            <button
              type="button"
              onClick={handleReset}
              className="rounded-md border border-slate-300 px-3 py-1.5 text-xs font-medium text-slate-700 hover:bg-slate-100"
            >
              새로 분석하기
            </button>
          </div>
          <LogAnalysisResultView result={analyzeLog.data} />
        </div>
      )}
    </div>
  )
}
