package com.monit.pingbell.global.dlq;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DlqReasonRedactorTest {

    @Test
    void redactRemovesUrlsEmailsAndTokensFromReason() {
        String jwt = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ1c2VyMUBleGFtcGxlLmNvbSJ9.EZV2c49qYWBzVjMXpS1i2P5wCpcJ7jQy9j8s2DqU4lA";
        String reason = """
                Failed url=https://api.example.com/health?token=plain-token \
                email=admin@example.com \
                webhook=https://hooks.slack.com/services/T000/B000/secret \
                authorization=Bearer abcd1234abcd1234 \
                apiKey=abc123secret \
                jwt=%s
                """.formatted(jwt);

        String redacted = DlqReasonRedactor.redact(reason);

        assertThat(redacted).contains("[REDACTED_URL]");
        assertThat(redacted).contains("[REDACTED_EMAIL]");
        assertThat(redacted).contains("authorization=[REDACTED_SECRET]");
        assertThat(redacted).contains("apiKey=[REDACTED_SECRET]");
        assertThat(redacted).contains("[REDACTED_TOKEN]");
        assertThat(redacted)
                .doesNotContain("https://api.example.com")
                .doesNotContain("admin@example.com")
                .doesNotContain("hooks.slack.com")
                .doesNotContain("abc123secret")
                .doesNotContain(jwt);
    }

    @Test
    void redactKeepsOperationalFieldsReadable() {
        String reason = "Invalid DLQ payload schema. topic=pingbell.health-check.requested.dlq monitorId=10";

        String redacted = DlqReasonRedactor.redact(reason);

        assertThat(redacted).isEqualTo(reason);
    }

    @Test
    void redactReturnsEmptyStringForNullOrBlankReason() {
        assertThat(DlqReasonRedactor.redact(null)).isEmpty();
        assertThat(DlqReasonRedactor.redact(" ")).isEmpty();
    }
}
