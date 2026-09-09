package com.monit.pingbell.logingestion.service;

import com.monit.pingbell.logingestion.config.LogIngestionProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * A short-lived, per-Monitor ring buffer of recently ingested (already-masked) log lines,
 * backed by Redis. This is intentionally not permanent storage - it exists only so Issue 3
 * (Incident-triggered automatic analysis) has something recent to read, and evicts itself via
 * TTL. A fixed-window-per-minute counter in the same Redis instance also rate-limits ingestion.
 */
@Service
@RequiredArgsConstructor
public class LogIngestionBufferService {

    private static final String BUFFER_KEY_PREFIX = "log-ingestion:buffer:";
    private static final String RATE_KEY_PREFIX = "log-ingestion:rate:";
    private static final Duration RATE_WINDOW_TTL = Duration.ofSeconds(70);

    private final StringRedisTemplate redisTemplate;
    private final LogIngestionProperties properties;
    private final Clock clock;

    public void append(Long tenantId, Long monitorId, List<String> lines) {
        if (lines.isEmpty()) {
            return;
        }
        String key = bufferKey(tenantId, monitorId);
        redisTemplate.opsForList().rightPushAll(key, lines);
        redisTemplate.opsForList().trim(key, -properties.getBufferMaxLines(), -1);
        redisTemplate.expire(key, Duration.ofSeconds(properties.getBufferTtlSeconds()));
    }

    public List<String> readAll(Long tenantId, Long monitorId) {
        List<String> values = redisTemplate.opsForList().range(bufferKey(tenantId, monitorId), 0, -1);
        return values == null ? List.of() : values;
    }

    /** @return true if this request is within the per-minute limit, false if it should be rejected. */
    public boolean tryConsumeRateLimit(Long tenantId, Long monitorId) {
        long epochMinute = Instant.now(clock).getEpochSecond() / 60;
        String key = RATE_KEY_PREFIX + tenantId + ":" + monitorId + ":" + epochMinute;
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redisTemplate.expire(key, RATE_WINDOW_TTL);
        }
        return count != null && count <= properties.getMaxRequestsPerMinute();
    }

    private String bufferKey(Long tenantId, Long monitorId) {
        return BUFFER_KEY_PREFIX + tenantId + ":" + monitorId;
    }
}
