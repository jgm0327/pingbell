package com.monit.pingbell.auth.service;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Test
    void createStoresHashedRefreshTokenWithTtl() {
        RefreshTokenService service = new RefreshTokenService(redisTemplate, 1000L, 2000L);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        RefreshToken token = service.create(1L, true);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(keyCaptor.capture(), org.mockito.ArgumentMatchers.eq("1|true"), org.mockito.ArgumentMatchers.eq(Duration.ofMillis(2000L)));

        assertThat(token.token()).isNotBlank();
        assertThat(token.expiresIn()).isEqualTo(2000L);
        assertThat(keyCaptor.getValue()).startsWith("auth:refresh:");
        assertThat(keyCaptor.getValue()).doesNotContain(token.token());
    }

    @Test
    void consumeDeletesTokenAndReturnsPayload() {
        RefreshTokenService service = new RefreshTokenService(redisTemplate, 1000L, 2000L);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn("1|false");

        RefreshTokenPayload payload = service.consume("refresh-token");

        assertThat(payload.memberId()).isEqualTo(1L);
        assertThat(payload.rememberMe()).isFalse();
        verify(redisTemplate).delete(org.mockito.ArgumentMatchers.startsWith("auth:refresh:"));
    }
}
