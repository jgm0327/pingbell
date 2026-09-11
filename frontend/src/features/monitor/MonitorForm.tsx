import { useForm } from 'react-hook-form'
import type { MonitorInput } from './types'

interface MonitorFormProps {
  defaultValues?: MonitorInput
  busy: boolean
  submitLabel: string
  errorMessage?: string | null
  onSubmit: (input: MonitorInput) => void
  onCancel: () => void
}

const NEW_MONITOR_DEFAULTS: MonitorInput = {
  name: '',
  url: '',
  intervalSeconds: 60,
  timeoutMillis: 5000,
  failureThreshold: 3,
  recoveryThreshold: 2,
}

export function MonitorForm({ defaultValues, busy, submitLabel, errorMessage, onSubmit, onCancel }: MonitorFormProps) {
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<MonitorInput>({ defaultValues: defaultValues ?? NEW_MONITOR_DEFAULTS })

  const submit = handleSubmit((values) => onSubmit(values))

  return (
    <form onSubmit={submit} noValidate className="flex max-w-lg flex-col gap-4">
      <div>
        <label htmlFor="monitor-name" className="block text-sm font-medium text-slate-700">
          이름
        </label>
        <input
          id="monitor-name"
          type="text"
          className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-sky-500 focus:outline-none focus:ring-1 focus:ring-sky-500"
          aria-invalid={!!errors.name}
          aria-describedby={errors.name ? 'monitor-name-error' : undefined}
          {...register('name', {
            required: '이름을 입력해주세요.',
            maxLength: { value: 100, message: '100자 이하로 입력해주세요.' },
          })}
        />
        {errors.name && (
          <p id="monitor-name-error" className="mt-1 text-sm text-red-600">
            {errors.name.message}
          </p>
        )}
      </div>

      <div>
        <label htmlFor="monitor-url" className="block text-sm font-medium text-slate-700">
          URL
        </label>
        <input
          id="monitor-url"
          type="text"
          placeholder="https://example.com/health"
          className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-sky-500 focus:outline-none focus:ring-1 focus:ring-sky-500"
          aria-invalid={!!errors.url}
          aria-describedby={errors.url ? 'monitor-url-error' : undefined}
          {...register('url', {
            required: 'URL을 입력해주세요.',
            maxLength: { value: 2048, message: '2048자 이하로 입력해주세요.' },
            pattern: { value: /^https?:\/\/.+/i, message: 'http:// 또는 https://로 시작하는 URL을 입력해주세요.' },
          })}
        />
        {errors.url && (
          <p id="monitor-url-error" className="mt-1 text-sm text-red-600">
            {errors.url.message}
          </p>
        )}
      </div>

      <div className="grid grid-cols-2 gap-4">
        <div>
          <label htmlFor="monitor-interval" className="block text-sm font-medium text-slate-700">
            체크 주기 (초)
          </label>
          <input
            id="monitor-interval"
            type="number"
            className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-sky-500 focus:outline-none focus:ring-1 focus:ring-sky-500"
            aria-invalid={!!errors.intervalSeconds}
            aria-describedby={errors.intervalSeconds ? 'monitor-interval-error' : undefined}
            {...register('intervalSeconds', {
              required: '체크 주기를 입력해주세요.',
              valueAsNumber: true,
              min: { value: 10, message: '10초 이상이어야 합니다.' },
              max: { value: 3600, message: '3600초 이하여야 합니다.' },
            })}
          />
          {errors.intervalSeconds && (
            <p id="monitor-interval-error" className="mt-1 text-sm text-red-600">
              {errors.intervalSeconds.message}
            </p>
          )}
        </div>

        <div>
          <label htmlFor="monitor-timeout" className="block text-sm font-medium text-slate-700">
            Timeout (ms)
          </label>
          <input
            id="monitor-timeout"
            type="number"
            className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-sky-500 focus:outline-none focus:ring-1 focus:ring-sky-500"
            aria-invalid={!!errors.timeoutMillis}
            aria-describedby={errors.timeoutMillis ? 'monitor-timeout-error' : undefined}
            {...register('timeoutMillis', {
              required: 'Timeout을 입력해주세요.',
              valueAsNumber: true,
              min: { value: 100, message: '100ms 이상이어야 합니다.' },
              max: { value: 30000, message: '30000ms 이하여야 합니다.' },
            })}
          />
          {errors.timeoutMillis && (
            <p id="monitor-timeout-error" className="mt-1 text-sm text-red-600">
              {errors.timeoutMillis.message}
            </p>
          )}
        </div>

        <div>
          <label htmlFor="monitor-failure-threshold" className="block text-sm font-medium text-slate-700">
            장애 판정 기준 (연속 실패 횟수)
          </label>
          <input
            id="monitor-failure-threshold"
            type="number"
            className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-sky-500 focus:outline-none focus:ring-1 focus:ring-sky-500"
            aria-invalid={!!errors.failureThreshold}
            aria-describedby={errors.failureThreshold ? 'monitor-failure-threshold-error' : undefined}
            {...register('failureThreshold', {
              required: '장애 판정 기준을 입력해주세요.',
              valueAsNumber: true,
              min: { value: 1, message: '1 이상이어야 합니다.' },
              max: { value: 20, message: '20 이하여야 합니다.' },
            })}
          />
          {errors.failureThreshold && (
            <p id="monitor-failure-threshold-error" className="mt-1 text-sm text-red-600">
              {errors.failureThreshold.message}
            </p>
          )}
        </div>

        <div>
          <label htmlFor="monitor-recovery-threshold" className="block text-sm font-medium text-slate-700">
            복구 판정 기준 (연속 성공 횟수)
          </label>
          <input
            id="monitor-recovery-threshold"
            type="number"
            className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-sky-500 focus:outline-none focus:ring-1 focus:ring-sky-500"
            aria-invalid={!!errors.recoveryThreshold}
            aria-describedby={errors.recoveryThreshold ? 'monitor-recovery-threshold-error' : undefined}
            {...register('recoveryThreshold', {
              required: '복구 판정 기준을 입력해주세요.',
              valueAsNumber: true,
              min: { value: 1, message: '1 이상이어야 합니다.' },
              max: { value: 20, message: '20 이하여야 합니다.' },
            })}
          />
          {errors.recoveryThreshold && (
            <p id="monitor-recovery-threshold-error" className="mt-1 text-sm text-red-600">
              {errors.recoveryThreshold.message}
            </p>
          )}
        </div>
      </div>

      {errorMessage && (
        <p role="alert" className="text-sm text-red-600">
          {errorMessage}
        </p>
      )}

      <div className="flex justify-end gap-2">
        <button
          type="button"
          onClick={onCancel}
          disabled={busy}
          className="rounded-md border border-slate-300 px-4 py-2 text-sm font-medium text-slate-700 hover:bg-slate-100 disabled:cursor-not-allowed disabled:opacity-60"
        >
          취소
        </button>
        <button
          type="submit"
          disabled={busy}
          className="rounded-md bg-sky-600 px-4 py-2 text-sm font-semibold text-white hover:bg-sky-700 disabled:cursor-not-allowed disabled:opacity-60"
        >
          {busy ? '저장 중…' : submitLabel}
        </button>
      </div>
    </form>
  )
}
