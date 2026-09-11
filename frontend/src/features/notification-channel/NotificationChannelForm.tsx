import { useForm } from 'react-hook-form'
import type { NotificationChannelCreateInput, NotificationChannelType } from './types'

interface NotificationChannelFormProps {
  busy: boolean
  errorMessage?: string | null
  onSubmit: (input: NotificationChannelCreateInput) => void
}

const TYPE_OPTIONS: { value: NotificationChannelType; label: string }[] = [
  { value: 'EMAIL', label: '이메일' },
  { value: 'SLACK', label: 'Slack' },
  { value: 'DISCORD', label: 'Discord' },
]

const TARGET_PLACEHOLDER: Record<NotificationChannelType, string> = {
  EMAIL: 'you@example.com',
  SLACK: 'https://hooks.slack.com/services/...',
  DISCORD: 'https://discord.com/api/webhooks/...',
}

export function NotificationChannelForm({ busy, errorMessage, onSubmit }: NotificationChannelFormProps) {
  const {
    register,
    handleSubmit,
    watch,
    reset,
    formState: { errors },
  } = useForm<NotificationChannelCreateInput>({ defaultValues: { type: 'EMAIL', target: '' } })
  const type = watch('type')

  const submit = handleSubmit((values) => {
    onSubmit(values)
    reset({ type: values.type, target: '' })
  })

  return (
    <form onSubmit={submit} noValidate className="flex flex-wrap items-end gap-3">
      <div>
        <label htmlFor="channel-type" className="block text-sm font-medium text-slate-700">
          채널 종류
        </label>
        <select
          id="channel-type"
          className="mt-1 rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-sky-500 focus:outline-none focus:ring-1 focus:ring-sky-500"
          {...register('type')}
        >
          {TYPE_OPTIONS.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </select>
      </div>

      <div className="min-w-[240px] flex-1">
        <label htmlFor="channel-target" className="block text-sm font-medium text-slate-700">
          {type === 'EMAIL' ? '이메일 주소' : 'Webhook URL'}
        </label>
        <input
          id="channel-target"
          type="text"
          placeholder={TARGET_PLACEHOLDER[type]}
          className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-sky-500 focus:outline-none focus:ring-1 focus:ring-sky-500"
          aria-invalid={!!errors.target}
          aria-describedby={errors.target ? 'channel-target-error' : undefined}
          {...register('target', {
            required: '대상을 입력해주세요.',
            maxLength: { value: 500, message: '500자 이하로 입력해주세요.' },
          })}
        />
        {errors.target && (
          <p id="channel-target-error" className="mt-1 text-sm text-red-600">
            {errors.target.message}
          </p>
        )}
      </div>

      <button
        type="submit"
        disabled={busy}
        className="rounded-md bg-sky-600 px-4 py-2 text-sm font-semibold text-white hover:bg-sky-700 disabled:cursor-not-allowed disabled:opacity-60"
      >
        {busy ? '추가 중…' : '채널 추가'}
      </button>

      {errorMessage && (
        <p role="alert" className="w-full text-sm text-red-600">
          {errorMessage}
        </p>
      )}
    </form>
  )
}
