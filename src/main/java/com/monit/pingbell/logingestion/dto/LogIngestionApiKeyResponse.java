package com.monit.pingbell.logingestion.dto;

import com.monit.pingbell.logingestion.domain.LogIngestionApiKey;

import java.time.LocalDateTime;

public record LogIngestionApiKeyResponse(
        Long id,
        String keyPrefix,
        LocalDateTime createdAt,
        LocalDateTime lastUsedAt,
        boolean revoked
) {
    public static LogIngestionApiKeyResponse from(LogIngestionApiKey key) {
        return new LogIngestionApiKeyResponse(
                key.getId(), key.getKeyPrefix(), key.getCreatedAt(), key.getLastUsedAt(), key.isRevoked());
    }
}
