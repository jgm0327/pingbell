package com.monit.pingbell.logingestion.service;

import com.monit.pingbell.logingestion.domain.LogIngestionApiKey;
import com.monit.pingbell.logingestion.dto.LogIngestionApiKeyIssueResponse;
import com.monit.pingbell.logingestion.dto.LogIngestionApiKeyResponse;
import com.monit.pingbell.logingestion.exception.LogIngestionException;
import com.monit.pingbell.logingestion.repository.LogIngestionApiKeyRepository;
import com.monit.pingbell.logingestion.security.AuthenticatedIngestionKey;
import com.monit.pingbell.monitor.repository.MonitorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class LogIngestionApiKeyService {

    private static final String KEY_PREFIX = "pgbl_";
    private static final int KEY_ENTROPY_BYTES = 32;
    private static final int DISPLAY_PREFIX_LENGTH = 12;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final LogIngestionApiKeyRepository apiKeyRepository;
    private final MonitorRepository monitorRepository;
    private final LogIngestionConfigTemplateService configTemplateService;
    private final Clock clock;

    @Transactional
    public LogIngestionApiKeyIssueResponse issue(Long memberId, Long monitorId) {
        requireOwnedMonitor(memberId, monitorId);

        String rawKey = generateRawKey();
        String keyPrefix = rawKey.substring(0, Math.min(rawKey.length(), DISPLAY_PREFIX_LENGTH));
        LogIngestionApiKey saved = apiKeyRepository.save(
                new LogIngestionApiKey(monitorId, memberId, sha256(rawKey), keyPrefix));

        // Only ever generated here: the raw key exists solely in this response and is never
        // persisted, so these ready-made snippets can't be regenerated for an existing key later.
        String fluentBitConfig = configTemplateService.fluentBitConfig(monitorId, rawKey);
        String dockerComposeSnippet = configTemplateService.dockerComposeSnippet(monitorId);

        return new LogIngestionApiKeyIssueResponse(
                saved.getId(), rawKey, keyPrefix, saved.getCreatedAt(), fluentBitConfig, dockerComposeSnippet);
    }

    @Transactional(readOnly = true)
    public List<LogIngestionApiKeyResponse> list(Long memberId, Long monitorId) {
        requireOwnedMonitor(memberId, monitorId);
        return apiKeyRepository.findAllByTenantIdAndMonitorIdOrderByCreatedAtDesc(memberId, monitorId).stream()
                .map(LogIngestionApiKeyResponse::from)
                .toList();
    }

    @Transactional
    public void revoke(Long memberId, Long monitorId, Long keyId) {
        requireOwnedMonitor(memberId, monitorId);
        LogIngestionApiKey key = apiKeyRepository.findByIdAndTenantIdAndMonitorId(keyId, memberId, monitorId)
                .orElseThrow(() -> new LogIngestionException(
                        HttpStatus.NOT_FOUND, "LOG_INGESTION_API_KEY_NOT_FOUND", "API key not found."));
        key.revoke(LocalDateTime.now(clock));
    }

    /** Called from {@link com.monit.pingbell.logingestion.security.LogIngestionApiKeyAuthenticationFilter}
     * on every ingestion request - never throws, an unknown/revoked key is simply not authenticated. */
    @Transactional
    public Optional<AuthenticatedIngestionKey> authenticate(String rawKey) {
        return apiKeyRepository.findByKeyHash(sha256(rawKey))
                .filter(key -> !key.isRevoked())
                .map(key -> {
                    key.recordUse(LocalDateTime.now(clock));
                    return new AuthenticatedIngestionKey(key.getMonitorId(), key.getTenantId());
                });
    }

    private void requireOwnedMonitor(Long memberId, Long monitorId) {
        if (monitorRepository.findByIdAndMemberIdAndDeletedAtIsNull(monitorId, memberId).isEmpty()) {
            throw new LogIngestionException(HttpStatus.NOT_FOUND, "MONITOR_NOT_FOUND", "Monitor not found.");
        }
    }

    private String generateRawKey() {
        byte[] bytes = new byte[KEY_ENTROPY_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return KEY_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available.", e);
        }
    }
}
