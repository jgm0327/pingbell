import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  useSyncExternalStore,
  type ReactNode,
} from 'react'
import * as authApi from './api'
import { ApiError } from '../../shared/api/httpClient'
import {
  getSession,
  setSession,
  subscribeSession,
  subscribeSessionExpired,
  type Session,
} from '../../shared/api/session'

interface AuthContextValue {
  session: Session | null
  busy: boolean
  error: string | null
  /** Set only when a previous session was cleared because refreshing it failed (not on explicit logout). */
  expiredMessage: string | null
  signup: (email: string, password: string) => Promise<void>
  login: (email: string, password: string, rememberMe: boolean) => Promise<void>
  logout: () => void
  clearError: () => void
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const session = useSyncExternalStore(subscribeSession, getSession)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [expiredMessage, setExpiredMessage] = useState<string | null>(null)

  useEffect(
    () => subscribeSessionExpired(() => setExpiredMessage('세션이 만료되어 다시 로그인해주세요.')),
    [],
  )

  const signup = useCallback(async (email: string, password: string) => {
    setBusy(true)
    setError(null)
    try {
      const token = await authApi.signup(email, password)
      setSession({ email, accessToken: token.accessToken, refreshToken: token.refreshToken })
      setExpiredMessage(null)
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '회원가입에 실패했습니다.')
      throw e
    } finally {
      setBusy(false)
    }
  }, [])

  const login = useCallback(async (email: string, password: string, rememberMe: boolean) => {
    setBusy(true)
    setError(null)
    try {
      const token = await authApi.login(email, password, rememberMe)
      setSession({ email, accessToken: token.accessToken, refreshToken: token.refreshToken })
      setExpiredMessage(null)
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '로그인에 실패했습니다.')
      throw e
    } finally {
      setBusy(false)
    }
  }, [])

  const logout = useCallback(() => {
    const current = getSession()
    if (current) {
      authApi.logout(current.refreshToken).catch(() => {
        // best effort; the token is discarded client-side regardless
      })
    }
    setSession(null)
  }, [])

  const clearError = useCallback(() => setError(null), [])

  const value = useMemo<AuthContextValue>(
    () => ({ session, busy, error, expiredMessage, signup, login, logout, clearError }),
    [session, busy, error, expiredMessage, signup, login, logout, clearError],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider')
  }
  return context
}
