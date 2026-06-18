package com.monit.pingbell.global.security.auth;

public record AuthenticatedMember(
        Long id,
        String email
) {
}
