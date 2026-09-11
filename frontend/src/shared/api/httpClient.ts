import axios, { type AxiosError, type InternalAxiosRequestConfig } from 'axios'
import { getSession, notifySessionExpired, setSession } from './session'
import type { ErrorResponse } from './types'
import type { TokenResponse } from '../../features/auth/types'

export class ApiError extends Error {
  status: number
  code?: string

  constructor(status: number, message: string, code?: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
  }
}

declare module 'axios' {
  interface InternalAxiosRequestConfig {
    _retried?: boolean
  }
}

export const httpClient = axios.create()

httpClient.interceptors.request.use((config) => {
  const session = getSession()
  if (session) {
    config.headers.set('Authorization', `Bearer ${session.accessToken}`)
  }
  return config
})

// The access token is short-lived by design (see application.yml `jwt.access-token-expiration-ms`,
// currently 6 minutes) with a much longer-lived refresh token backing it. A single in-flight
// refresh is shared so concurrent auth failures don't each fire their own /api/auth/refresh call.
let refreshInFlight: Promise<string | null> | null = null

async function refreshAccessToken(): Promise<string | null> {
  const session = getSession()
  if (!session) {
    return null
  }
  if (!refreshInFlight) {
    refreshInFlight = doRefresh(session.refreshToken).finally(() => {
      refreshInFlight = null
    })
  }
  return refreshInFlight
}

async function doRefresh(refreshToken: string): Promise<string | null> {
  try {
    const response = await axios.post<TokenResponse>('/api/auth/refresh', { refreshToken })
    const current = getSession()
    if (!current) {
      return null
    }
    setSession({ ...current, accessToken: response.data.accessToken, refreshToken: response.data.refreshToken })
    return response.data.accessToken
  } catch {
    setSession(null)
    notifySessionExpired()
    return null
  }
}

httpClient.interceptors.response.use(
  (response) => response,
  async (error: AxiosError<(ErrorResponse & Record<string, unknown>) | undefined>) => {
    const config = error.config as InternalAxiosRequestConfig | undefined
    const status = error.response?.status
    const body = error.response?.data

    // This backend has no custom AuthenticationEntryPoint, so Spring Security's default kicks in
    // for a missing/invalid/expired JWT - which returns 403, NOT 401 (confirmed against the
    // backend's own tests). That response is the plain Spring Boot whitelabel error body
    // ({timestamp, status, error, path} - no `code`), which is how we tell it apart from a
    // genuine app-level 403 like MONITOR_ACCESS_DENIED (thrown via GlobalExceptionHandler, which
    // always includes `code`). Only the former is worth retrying after a token refresh.
    const looksLikeAuthFailure = (status === 401 || status === 403) && !!body && !body.code
    if (looksLikeAuthFailure && config && !config._retried && getSession()) {
      const refreshedToken = await refreshAccessToken()
      if (refreshedToken) {
        config._retried = true
        config.headers.set('Authorization', `Bearer ${refreshedToken}`)
        return httpClient(config)
      }
    }

    const message = body?.message ?? error.message
    const code = body?.code
    return Promise.reject(new ApiError(status ?? 0, message, code))
  },
)
