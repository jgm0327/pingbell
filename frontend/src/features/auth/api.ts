import { httpClient } from '../../shared/api/httpClient'
import type { TokenResponse } from './types'

export function signup(email: string, password: string): Promise<TokenResponse> {
  return httpClient.post<TokenResponse>('/api/auth/signup', { email, password }).then((r) => r.data)
}

export function login(email: string, password: string, rememberMe: boolean): Promise<TokenResponse> {
  return httpClient.post<TokenResponse>('/api/auth/login', { email, password, rememberMe }).then((r) => r.data)
}

export function logout(refreshToken: string): Promise<void> {
  return httpClient.post<void>('/api/auth/logout', { refreshToken }).then(() => undefined)
}
