package com.monit.pingbell.auth.service;

public record RefreshTokenPayload(
        Long memberId,
        boolean rememberMe
) {}
