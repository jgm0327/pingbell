package com.monit.pingbell.auth.dto;

public record TokenResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        long refreshTokenExpiresIn
) {
    public static TokenResponse bearer(
            String accessToken,
            String refreshToken,
            long expiresIn,
            long refreshTokenExpiresIn
    ) {
        return new TokenResponse(accessToken, refreshToken, "Bearer", expiresIn, refreshTokenExpiresIn);
    }
}
