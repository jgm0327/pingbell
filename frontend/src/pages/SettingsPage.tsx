import { useAuth } from '../features/auth/AuthContext'

export function SettingsPage() {
  const { session, logout } = useAuth()

  return (
    <div className="max-w-md rounded-lg border border-slate-200 bg-white p-6">
      <h2 className="mb-4 text-lg font-semibold text-slate-800">계정</h2>
      <dl className="mb-6 space-y-2 text-sm">
        <div className="flex justify-between">
          <dt className="text-slate-500">이메일</dt>
          <dd className="font-medium text-slate-800">{session?.email}</dd>
        </div>
      </dl>
      <button
        type="button"
        onClick={logout}
        className="rounded-md border border-red-200 px-4 py-2 text-sm font-medium text-red-600 hover:bg-red-50"
      >
        로그아웃
      </button>
    </div>
  )
}
