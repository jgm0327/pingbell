package com.monit.pingbell.notification.crypto;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class NotificationTargetCrypto {

    private static final String PREFIX = "enc:v1:";
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int IV_LENGTH_BYTES = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private static volatile SecretKeySpec keySpec;

    public NotificationTargetCrypto(
            @Value("${pingbell.security.notification-target-key:${JWT_SECRET:}}") String secret
    ) {
        configure(secret);
    }

    static void configure(String secret) {
        if (secret == null || secret.isBlank()) {
            keySpec = null;
            return;
        }
        keySpec = new SecretKeySpec(sha256(secret), "AES");
    }

    static String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isBlank() || plaintext.startsWith(PREFIX)) {
            return plaintext;
        }

        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            SECURE_RANDOM.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, requireKey(), new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            ByteBuffer payload = ByteBuffer.allocate(iv.length + ciphertext.length);
            payload.put(iv);
            payload.put(ciphertext);
            return PREFIX + Base64.getEncoder().encodeToString(payload.array());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encrypt notification target.", e);
        }
    }

    static String decrypt(String value) {
        if (value == null || value.isBlank() || !value.startsWith(PREFIX)) {
            return value;
        }

        try {
            byte[] payload = Base64.getDecoder().decode(value.substring(PREFIX.length()));
            ByteBuffer buffer = ByteBuffer.wrap(payload);
            byte[] iv = new byte[IV_LENGTH_BYTES];
            buffer.get(iv);
            byte[] ciphertext = new byte[buffer.remaining()];
            buffer.get(ciphertext);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, requireKey(), new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to decrypt notification target.", e);
        }
    }

    static boolean isEncrypted(String value) {
        return value != null && value.startsWith(PREFIX);
    }

    private static SecretKeySpec requireKey() {
        SecretKeySpec currentKeySpec = keySpec;
        if (currentKeySpec == null) {
            throw new IllegalStateException("Notification target encryption key is not configured.");
        }
        return currentKeySpec;
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to derive notification target encryption key.", e);
        }
    }
}
