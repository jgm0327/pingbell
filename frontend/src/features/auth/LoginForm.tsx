import { useForm } from 'react-hook-form'
import { useNavigate } from 'react-router-dom'
import { useAuth } from './AuthContext'

interface LoginFormValues {
  email: string
  password: string
  rememberMe: boolean
}

export function LoginForm() {
  const { login, busy, error, expiredMessage, clearError } = useAuth()
  const navigate = useNavigate()
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<LoginFormValues>({ defaultValues: { rememberMe: true } })

  const onSubmit = handleSubmit(async (values) => {
    try {
      await login(values.email, values.password, values.rememberMe)
      navigate('/dashboard', { replace: true })
    } catch {
      // error is surfaced via useAuth().error
    }
  })

  return (
    <form onSubmit={onSubmit} className="flex flex-col gap-4" onFocus={clearError} noValidate>
      {expiredMessage && (
        <p role="alert" className="rounded-md bg-amber-50 px-3 py-2 text-sm text-amber-800">
          {expiredMessage}
        </p>
      )}

      <div>
        <label htmlFor="login-email" className="block text-sm font-medium text-slate-700">
          이메일
        </label>
        <input
          id="login-email"
          type="email"
          autoComplete="email"
          className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-sky-500 focus:outline-none focus:ring-1 focus:ring-sky-500"
          aria-invalid={!!errors.email}
          aria-describedby={errors.email ? 'login-email-error' : undefined}
          {...register('email', { required: '이메일을 입력해주세요.' })}
        />
        {errors.email && (
          <p id="login-email-error" className="mt-1 text-sm text-red-600">
            {errors.email.message}
          </p>
        )}
      </div>

      <div>
        <label htmlFor="login-password" className="block text-sm font-medium text-slate-700">
          비밀번호
        </label>
        <input
          id="login-password"
          type="password"
          autoComplete="current-password"
          className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-sky-500 focus:outline-none focus:ring-1 focus:ring-sky-500"
          aria-invalid={!!errors.password}
          aria-describedby={errors.password ? 'login-password-error' : undefined}
          {...register('password', { required: '비밀번호를 입력해주세요.' })}
        />
        {errors.password && (
          <p id="login-password-error" className="mt-1 text-sm text-red-600">
            {errors.password.message}
          </p>
        )}
      </div>

      <label className="flex items-center gap-2 text-sm text-slate-600">
        <input type="checkbox" className="h-4 w-4 rounded border-slate-300" {...register('rememberMe')} />
        로그인 상태 유지
      </label>

      {error && (
        <p role="alert" className="text-sm text-red-600">
          {error}
        </p>
      )}

      <button
        type="submit"
        disabled={busy}
        className="rounded-md bg-sky-600 px-4 py-2 text-sm font-semibold text-white hover:bg-sky-700 disabled:cursor-not-allowed disabled:opacity-60"
      >
        {busy ? '로그인 중…' : '로그인'}
      </button>
    </form>
  )
}
