package com.monit.pingbell.logingestion.domain;

import com.monit.pingbell.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * A long-lived, revocable credential scoped to one Monitor, used by unattended log shippers
 * (e.g. Fluent Bit) to authenticate against the log ingestion endpoint. Unlike the human-facing
 * JWT access token, this is never rotated automatically and has no fixed expiry - only explicit
 * revocation. The raw key is never stored: only its SHA-256 hash, so a database leak alone
 * cannot be used to forge or replay a key.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "log_ingestion_api_keys")
public class LogIngestionApiKey extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "monitor_id", nullable = false, updatable = false)
    private Long monitorId;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private Long tenantId;

    @Column(name = "key_hash", nullable = false, updatable = false, unique = true, length = 64)
    private String keyHash;

    @Column(name = "key_prefix", nullable = false, updatable = false, length = 20)
    private String keyPrefix;

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    public LogIngestionApiKey(Long monitorId, Long tenantId, String keyHash, String keyPrefix) {
        this.monitorId = monitorId;
        this.tenantId = tenantId;
        this.keyHash = keyHash;
        this.keyPrefix = keyPrefix;
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public void revoke(LocalDateTime now) {
        if (revokedAt == null) {
            revokedAt = now;
        }
    }

    public void recordUse(LocalDateTime now) {
        this.lastUsedAt = now;
    }
}
