import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { StatusBadge } from '../../shared/components/StatusBadge'
import { ConfirmModal } from '../../shared/components/ConfirmModal'
import { ApiError } from '../../shared/api/httpClient'
import { notificationChannelTypeLabel } from './notificationChannelMeta'
import { useDisableNotificationChannel, useTestSendNotificationChannel, useUpdateNotificationChannel } from './queries'
import type { NotificationChannel } from './types'

interface NotificationChannelRowProps {
  channel: NotificationChannel
}

interface EditFormValues {
  target: string
}

export function NotificationChannelRow({ channel }: NotificationChannelRowProps) {
  const [editing, setEditing] = useState(false)
  const [confirmingDisable, setConfirmingDisable] = useState(false)
  const [rowError, setRowError] = useState<string | null>(null)
  const [testResult, setTestResult] = useState<{ success: boolean; message: string } | null>(null)

  const updateChannel = useUpdateNotificationChannel(channel.publicId)
  const disableChannel = useDisableNotificationChannel()
  const testSend = useTestSendNotificationChannel()

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<EditFormValues>({ defaultValues: { target: '' } })

  const submitEdit = handleSubmit((values) => {
    setRowError(null)
    updateChannel.mutate(
      { target: values.target },
      {
        onSuccess: () => setEditing(false),
        onError: (error) => setRowError(error instanceof ApiError ? error.message : '수정에 실패했습니다.'),
      },
    )
  })

  // useForm's state lives at the component level, not the <input>'s mount lifecycle - toggling
  // `editing` unmounts/remounts the form JSX but NOT the hook itself, so without this the previous
  // session's typed value (and any leftover validation error) would still be there next time.
  const toggleEditing = () => {
    if (!editing) {
      reset({ target: '' })
    }
    setRowError(null)
    setEditing((value) => !value)
  }

  const handleTestSend = () => {
    setTestResult(null)
    testSend.mutate(channel.publicId, {
      onSuccess: (result) => setTestResult({ success: result.success, message: result.message }),
      onError: (error) =>
        setTestResult({
          success: false,
          message: error instanceof ApiError ? error.message : '테스트 발송에 실패했습니다.',
        }),
    })
  }

  const confirmDisable = () => {
    setRowError(null)
    disableChannel.mutate(channel.publicId, {
      onSuccess: () => setConfirmingDisable(false),
      onError: (error) => {
        setRowError(error instanceof ApiError ? error.message : '비활성화에 실패했습니다.')
        setConfirmingDisable(false)
      },
    })
  }

  return (
    <div className="rounded-lg border border-slate-200 bg-white p-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="flex items-center gap-2">
          <span className="text-sm font-medium text-slate-800">{notificationChannelTypeLabel(channel.type)}</span>
          <StatusBadge label={channel.enabled ? '활성화' : '비활성화'} tone={channel.enabled ? 'success' : 'neutral'} />
        </div>
        <div className="flex gap-2">
          <button
            type="button"
            onClick={handleTestSend}
            disabled={testSend.isPending}
            className="rounded-md border border-slate-300 px-3 py-1.5 text-xs font-medium text-slate-700 hover:bg-slate-100 disabled:cursor-not-allowed disabled:opacity-60"
          >
            {testSend.isPending ? '발송 중…' : '테스트 발송'}
          </button>
          <button
            type="button"
            onClick={toggleEditing}
            className="rounded-md border border-slate-300 px-3 py-1.5 text-xs font-medium text-slate-700 hover:bg-slate-100"
          >
            {editing ? '취소' : channel.enabled ? '수정' : '수정 후 재활성화'}
          </button>
          {channel.enabled && (
            <button
              type="button"
              onClick={() => setConfirmingDisable(true)}
              className="rounded-md border border-red-200 px-3 py-1.5 text-xs font-medium text-red-600 hover:bg-red-50"
            >
              비활성화
            </button>
          )}
        </div>
      </div>

      {!editing && <p className="mt-1 text-sm text-slate-500">{channel.maskedTarget}</p>}

      {editing && (
        <form onSubmit={submitEdit} noValidate className="mt-2 flex flex-wrap items-start gap-2">
          <div className="min-w-[240px] flex-1">
            <label className="sr-only" htmlFor={`channel-${channel.publicId}-target`}>
              새 대상 값
            </label>
            <input
              id={`channel-${channel.publicId}-target`}
              type="text"
              placeholder="새 대상 값을 입력하세요 (원래 값은 보안상 다시 보여줄 수 없습니다)"
              aria-invalid={!!errors.target}
              aria-describedby={errors.target ? `channel-${channel.publicId}-target-error` : undefined}
              className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-sky-500 focus:outline-none focus:ring-1 focus:ring-sky-500"
              {...register('target', {
                required: '대상을 입력해주세요.',
                maxLength: { value: 500, message: '500자 이하로 입력해주세요.' },
              })}
            />
            {errors.target && (
              <p id={`channel-${channel.publicId}-target-error`} className="mt-1 text-sm text-red-600">
                {errors.target.message}
              </p>
            )}
          </div>
          <button
            type="submit"
            disabled={updateChannel.isPending}
            className="rounded-md bg-sky-600 px-3 py-2 text-sm font-semibold text-white hover:bg-sky-700 disabled:cursor-not-allowed disabled:opacity-60"
          >
            {updateChannel.isPending ? '저장 중…' : '저장'}
          </button>
        </form>
      )}

      {testResult && (
        <p className={`mt-2 text-sm ${testResult.success ? 'text-emerald-700' : 'text-red-600'}`}>
          {testResult.message}
        </p>
      )}
      {rowError && (
        <p role="alert" className="mt-2 text-sm text-red-600">
          {rowError}
        </p>
      )}

      <ConfirmModal
        open={confirmingDisable}
        title="알림 채널을 비활성화할까요?"
        description="비활성화하면 이 채널로는 더 이상 알림이 발송되지 않습니다. 다시 활성화하려면 대상 값을 수정해야 합니다."
        confirmLabel="비활성화"
        danger
        busy={disableChannel.isPending}
        onConfirm={confirmDisable}
        onCancel={() => setConfirmingDisable(false)}
      />
    </div>
  )
}
