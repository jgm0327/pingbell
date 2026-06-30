package com.monit.pingbell.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RefreshTokenService {

    private static final String KEY_PREFIX = "auth:refresh:";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final StringRedisTemplate redisTemplate;
    private final long refreshTokenExpirationMs;
    private final long rememberMeRefreshTokenExpirationMs;

    public RefreshTokenService(
            StringRedisTemplate redisTemplate,
            @Value("${jwt.refresh-token-expiration-ms}") long refreshTokenExpirationMs,
            @Value("${jwt.remember-me-refresh-token-expiration-ms}") long rememberMeRefreshTokenExpirationMs
    ) {
        this.redisTemplate = redisTemplate;
        this.refreshTokenExpirationMs = refreshTokenExpirationMs;
        this.rememberMeRefreshTokenExpirationMs = rememberMeRefreshTokenExpirationMs;
    }

    public RefreshToken create(Long memberId, boolean rememberMe) {
        String token = createRandomToken();
        long expiresIn = rememberMe ? rememberMeRefreshTokenExpirationMs : refreshTokenExpirationMs;
        redisTemplate.opsForValue().set(key(token), serialize(new RefreshTokenPayload(memberId, rememberMe)), Duration.ofMillis(expiresIn));
        return new RefreshToken(token, expiresIn);
    }

    public RefreshTokenPayload consume(String token) {
        String key = key(token);
        String value = redisTemplate.opsForValue().get(key);
        if (value == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token.");
        }

        redisTemplate.delete(key);
        return deserialize(value);
    }

    public void revoke(String token) {
        redisTemplate.delete(key(token));
    }

    private String createRandomToken() {
        byte[] bytes = new byte[64];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String key(String token) {
        return KEY_PREFIX + sha256(token);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available.", e);
        }
    }

    private String serialize(RefreshTokenPayload payload) {
        return payload.memberId() + "|" + payload.rememberMe();
    }

    private RefreshTokenPayload deserialize(String value) {
        String[] parts = value.split("\\|", 2);
        if (parts.length != 2) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token.");
        }
        return new RefreshTokenPayload(Long.valueOf(parts[0]), Boolean.parseBoolean(parts[1]));
    }
}
