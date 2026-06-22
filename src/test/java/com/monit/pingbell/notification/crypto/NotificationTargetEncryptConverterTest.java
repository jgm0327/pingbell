package com.monit.pingbell.notification.crypto;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotificationTargetEncryptConverterTest {

    private final NotificationTargetEncryptConverter converter = new NotificationTargetEncryptConverter();

    @BeforeEach
    void setUp() {
        NotificationTargetCrypto.configure("test-notification-target-encryption-key");
    }

    @Test
    void convertToDatabaseColumnEncryptsPlainTarget() {
        String encrypted = converter.convertToDatabaseColumn("https://hooks.slack.com/services/test");

        assertThat(encrypted).startsWith("enc:v1:");
        assertThat(encrypted).doesNotContain("hooks.slack.com");
        assertThat(encrypted).doesNotContain("test");
    }

    @Test
    void convertToEntityAttributeDecryptsEncryptedTarget() {
        String plaintext = "https://discord.com/api/webhooks/test";
        String encrypted = converter.convertToDatabaseColumn(plaintext);

        String decrypted = converter.convertToEntityAttribute(encrypted);

        assertThat(decrypted).isEqualTo(plaintext);
    }

    @Test
    void convertToEntityAttributeKeepsLegacyPlainTargetReadable() {
        String legacyPlaintext = "legacy@example.com";

        String converted = converter.convertToEntityAttribute(legacyPlaintext);

        assertThat(converted).isEqualTo(legacyPlaintext);
    }

    @Test
    void convertToDatabaseColumnRequiresEncryptionKey() {
        NotificationTargetCrypto.configure("");

        assertThatThrownBy(() -> converter.convertToDatabaseColumn("user@example.com"))
                .hasMessageContaining("Failed to encrypt notification target");
    }
}
