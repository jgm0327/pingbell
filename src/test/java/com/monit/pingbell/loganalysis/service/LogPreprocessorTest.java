package com.monit.pingbell.loganalysis.service;

import org.junit.jupiter.api.Test;

import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class LogPreprocessorTest {

    private final LogPreprocessor preprocessor = new LogPreprocessor();

    @Test
    void masksCredentialsAndPersonalData() {
        String raw = """
                Authorization: Bearer top-secret-token
                token=eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.signature1234567890
                password=hunter2 secret:my-secret api_key=sk-test
                {"password":"json-secret","apiKey":"json-key"}
                user@example.com connected from 192.168.0.10
                """;

        String result = preprocessor.process(raw).content();

        assertThat(result).doesNotContain(
                "top-secret-token", "eyJhbGci", "hunter2", "my-secret", "sk-test",
                "json-secret", "json-key",
                "user@example.com", "192.168.0.10"
        );
        assertThat(result).contains("[REDACTED]");
    }

    @Test
    void masksKnownVendorSecretsAndKoreanPii() {
        String raw = """
                OpenAI key leaked: sk-proj-abcdefghijklmnopqrstuvwxyz0123456789
                Slack token: xoxb-FAKE-TEST-VALUE-NOT-A-REAL-SLACK-TOKEN
                GitHub token: ghp_1234567890abcdefghijklmnopqrstuvwx
                AWS access key: AKIAABCDEFGHIJKLMNOP
                Google API key: AIzaSyD1234567890abcdefghijklmnopqrst
                phone=010-1234-5678 rrn=901231-1234567 card=1234-5678-9012-3456
                """;

        String result = preprocessor.process(raw).content();

        assertThat(result).doesNotContain(
                "sk-proj-abcdefghijklmnopqrstuvwxyz0123456789",
                "xoxb-FAKE-TEST-VALUE-NOT-A-REAL-SLACK-TOKEN",
                "ghp_1234567890abcdefghijklmnopqrstuvwx",
                "AKIAABCDEFGHIJKLMNOP",
                "AIzaSyD1234567890abcdefghijklmnopqrst",
                "010-1234-5678",
                "901231-1234567",
                "1234-5678-9012-3456"
        );
    }

    @Test
    void keepsOnlyLastTwoThousandLines() {
        String raw = IntStream.rangeClosed(1, 2_100)
                .mapToObj(number -> "line-" + number)
                .collect(Collectors.joining("\n"));

        ProcessedLog result = preprocessor.process(raw);

        assertThat(result.truncated()).isTrue();
        assertThat(result.content()).doesNotContain("line-100\n");
        assertThat(result.content()).startsWith("line-101\n");
        assertThat(result.content()).endsWith("line-2100");
    }

    @Test
    void keepsAtMostOneHundredThousandCharactersFromTail() {
        String raw = "x".repeat(LogPreprocessor.MAX_ANALYZED_CHARACTERS + 100);

        ProcessedLog result = preprocessor.process(raw);

        assertThat(result.truncated()).isTrue();
        assertThat(result.analyzedCharacters()).isEqualTo(LogPreprocessor.MAX_ANALYZED_CHARACTERS);
        assertThat(result.content()).hasSize(LogPreprocessor.MAX_ANALYZED_CHARACTERS);
    }
}
