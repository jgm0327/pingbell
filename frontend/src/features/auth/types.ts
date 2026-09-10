// Mirrors com.monit.pingbell.auth.dto.TokenResponse.
export interface TokenResponse {
  accessToken: string
  refreshToken: string
  tokenType: string
  expiresIn: number
  refreshTokenExpiresIn: number
}
