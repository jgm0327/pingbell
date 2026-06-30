package com.monit.pingbell.auth.service;

public record RefreshToken(
        String token,
        long expiresIn
) {}
