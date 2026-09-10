import { useForm } from 'react-hook-form'
import { useNavigate } from 'react-router-dom'
import { useAuth } from './AuthContext'

interface SignupFormValues {
  email: string
  password: string
  passwordConfirm: string
}

export function SignupForm() {
  const { signup, busy, error, clearError } = useAuth()
  const navigate = useNavigate()
  const {
    register,
    handleSubmit,
    watch,
    formState: { errors },
  } = useForm<SignupFormValues>()

  const onSubmit = handleSubmit(async (values) => {
    try {
      await signup(values.email, values.password)
      navigate('/dashboard', { replace: true })
    } catch {
      // error is surfaced via useAuth().error
    }
  })

  return (
    <form onSubmit={onSubmit} className="flex flex-col gap-4" onFocus={clearError} noValidate>
      <div>
        <label htmlFor="signup-email" className="block text-sm font-medium text-slate-700">
          이메일
        </label>
        <input
          id="signup-email"
          type="email"
          autoComplete="email"
          className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-sky-500 focus:outline-none focus:ring-1 focus:ring-sky-500"
          aria-invalid={!!errors.email}
          aria-describedby={errors.email ? 'signup-email-error' : undefined}
          {...register('email', { required: '이메일을 입력해주세요.' })}
        />
        {errors.email && (
          <p id="signup-email-error" className="mt-1 text-sm text-red-600">
            {errors.email.message}
          </p>
        )}
      </div>

      <div>
        <label htmlFor="signup-password" className="block text-sm font-medium text-slate-700">
          비밀번호 (8자 이상)
        </label>
        <input
          id="signup-password"
          type="password"
          autoComplete="new-password"
          className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-sky-500 focus:outline-none focus:ring-1 focus:ring-sky-500"
          aria-invalid={!!errors.password}
          aria-describedby={errors.password ? 'signup-password-error' : undefined}
          {...register('password', {
            required: '비밀번호를 입력해주세요.',
            minLength: { value: 8, message: '비밀번호는 8자 이상이어야 합니다.' },
          })}
        />
        {errors.password && (
          <p id="signup-password-error" className="mt-1 text-sm text-red-600">
            {errors.password.message}
          </p>
        )}
      </div>

      <div>
        <label htmlFor="signup-password-confirm" className="block text-sm font-medium text-slate-700">
          비밀번호 확인
        </label>
        <input
          id="signup-password-confirm"
          type="password"
          autoComplete="new-password"
          className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-sky-500 focus:outline-none focus:ring-1 focus:ring-sky-500"
          aria-invalid={!!errors.passwordConfirm}
          aria-describedby={errors.passwordConfirm ? 'signup-password-confirm-error' : undefined}
          {...register('passwordConfirm', {
            required: '비밀번호를 한 번 더 입력해주세요.',
            validate: (value) => value === watch('password') || '비밀번호가 일치하지 않습니다.',
          })}
        />
        {errors.passwordConfirm && (
          <p id="signup-password-confirm-error" className="mt-1 text-sm text-red-600">
            {errors.passwordConfirm.message}
          </p>
        )}
      </div>

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
        {busy ? '가입 중…' : '회원가입'}
      </button>
    </form>
  )
}
