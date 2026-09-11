import { Link } from 'react-router-dom'
import { SignupForm } from '../features/auth/SignupForm'

export function SignupPage() {
  return (
    <div className="flex min-h-screen items-center justify-center bg-slate-50 px-4">
      <div className="w-full max-w-sm rounded-xl border border-slate-200 bg-white p-8 shadow-sm">
        <h1 className="mb-1 text-xl font-bold text-slate-900">Pingbell 회원가입</h1>
        <p className="mb-6 text-sm text-slate-500">가입하면 기본 이메일 알림 채널이 자동으로 생성됩니다.</p>
        <SignupForm />
        <p className="mt-6 text-center text-sm text-slate-500">
          이미 계정이 있으신가요?{' '}
          <Link to="/login" className="font-medium text-sky-600 hover:underline">
            로그인
          </Link>
        </p>
      </div>
    </div>
  )
}
