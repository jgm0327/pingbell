import { Link } from 'react-router-dom'
import { LoginForm } from '../features/auth/LoginForm'

export function LoginPage() {
  return (
    <div className="flex min-h-screen items-center justify-center bg-slate-50 px-4">
      <div className="w-full max-w-sm rounded-xl border border-slate-200 bg-white p-8 shadow-sm">
        <h1 className="mb-1 text-xl font-bold text-slate-900">Pingbell 로그인</h1>
        <p className="mb-6 text-sm text-slate-500">서버 상태를 확인하고 장애를 관리하세요.</p>
        <LoginForm />
        <p className="mt-6 text-center text-sm text-slate-500">
          계정이 없으신가요?{' '}
          <Link to="/signup" className="font-medium text-sky-600 hover:underline">
            회원가입
          </Link>
        </p>
      </div>
    </div>
  )
}
