package com.monit.pingbell.logingestion.service;

import com.monit.pingbell.logingestion.config.LogIngestionProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LogIngestionBufferServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ListOperations<String, String> listOperations;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private final Clock clock = Clock.fixed(Instant.parse("2026-08-25T00:00:00Z"), ZoneOffset.UTC);
    private LogIngestionProperties properties;
    private LogIngestionBufferService service;

    @BeforeEach
    void setUp() {
        properties = new LogIngestionProperties();
        properties.setBufferMaxLines(3);
        properties.setBufferTtlSeconds(900);
        properties.setMaxRequestsPerMinute(2);
        service = new LogIngestionBufferService(redisTemplate, properties, clock);
    }

    @Test
    void appendPushesLinesTrimsToMaxLinesAndRefreshesTtl() {
        when(redisTemplate.opsForList()).thenReturn(listOperations);

        service.append(1L, 12L, List.of("line1", "line2"));

        String key = "log-ingestion:buffer:1:12";
        verify(listOperations).rightPushAll(key, List.of("line1", "line2"));
        verify(listOperations).trim(key, -3, -1);
        verify(redisTemplate).expire(key, Duration.ofSeconds(900));
    }

    @Test
    void appendDoesNothingForEmptyLines() {
        service.append(1L, 12L, List.of());

        verifyNoInteractions(redisTemplate);
    }

    @Test
    void readAllReturnsEmptyListWhenBufferMissing() {
        when(redisTemplate.opsForList()).thenReturn(listOperations);
        when(listOperations.range("log-ingestion:buffer:1:12", 0, -1)).thenReturn(null);

        assertThat(service.readAll(1L, 12L)).isEmpty();
    }

    @Test
    void tryConsumeRateLimitAllowsUpToConfiguredLimitPerMinuteThenRejects() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        String key = "log-ingestion:rate:1:12:" + (Instant.parse("2026-08-25T00:00:00Z").getEpochSecond() / 60);
        when(valueOperations.increment(key)).thenReturn(1L, 2L, 3L);

        assertThat(service.tryConsumeRateLimit(1L, 12L)).isTrue();
        assertThat(service.tryConsumeRateLimit(1L, 12L)).isTrue();
        assertThat(service.tryConsumeRateLimit(1L, 12L)).isFalse();

        verify(redisTemplate).expire(key, Duration.ofSeconds(70));
    }

    @Test
    void tryConsumeRateLimitOnlySetsExpiryOnFirstIncrementInWindow() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        String key = "log-ingestion:rate:1:12:" + (Instant.parse("2026-08-25T00:00:00Z").getEpochSecond() / 60);
        when(valueOperations.increment(key)).thenReturn(2L);

        service.tryConsumeRateLimit(1L, 12L);

        verify(redisTemplate, never()).expire(key, Duration.ofSeconds(70));
    }
}
