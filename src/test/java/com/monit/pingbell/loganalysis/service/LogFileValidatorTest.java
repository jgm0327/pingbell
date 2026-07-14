package com.monit.pingbell.loganalysis.service;

import com.monit.pingbell.loganalysis.exception.LogAnalysisException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LogFileValidatorTest {

    private final LogFileValidator validator = new LogFileValidator();

    @Test
    void acceptsOneUtf8TextLog() {
        MockMultipartFile file = file("server.log", "text/plain", "서버 오류 로그");

        ValidatedLogFile result = validator.validate(List.of(file));

        assertThat(result.content()).isEqualTo("서버 오류 로그");
        assertThat(result.originalSizeBytes()).isEqualTo(file.getSize());
    }

    @Test
    void rejectsMissingAndMultipleFiles() {
        assertCode(() -> validator.validate(List.of()), "LOG_FILE_REQUIRED");
        assertCode(() -> validator.validate(List.of(
                file("one.log", "text/plain", "one"),
                file("two.log", "text/plain", "two")
        )), "UNSUPPORTED_LOG_FILE_TYPE");
    }

    @Test
    void rejectsEmptyAndOversizedFiles() {
        assertCode(() -> validator.validate(List.of(
                new MockMultipartFile("file", "empty.log", "text/plain", new byte[0])
        )), "LOG_FILE_EMPTY");

        assertCode(() -> validator.validate(List.of(
                new MockMultipartFile(
                        "file",
                        "large.log",
                        "text/plain",
                        new byte[(int) LogFileValidator.MAX_FILE_SIZE_BYTES + 1]
                )
        )), "LOG_FILE_TOO_LARGE");
    }

    @Test
    void rejectsUnsupportedExtensionMimeAndBinaryContent() {
        assertCode(() -> validator.validate(List.of(file("server.csv", "text/plain", "error"))),
                "UNSUPPORTED_LOG_FILE_TYPE");
        assertCode(() -> validator.validate(List.of(file("server.log", "application/pdf", "error"))),
                "UNSUPPORTED_LOG_FILE_TYPE");
        assertCode(() -> validator.validate(List.of(new MockMultipartFile(
                "file", "archive.log", "application/octet-stream", new byte[]{0x50, 0x4B, 0x03, 0x04}
        ))), "UNSUPPORTED_LOG_FILE_TYPE");
        assertCode(() -> validator.validate(List.of(new MockMultipartFile(
                "file", "binary.txt", "text/plain", new byte[]{'a', 0, 'b'}
        ))), "UNSUPPORTED_LOG_FILE_TYPE");
    }

    @Test
    void rejectsMalformedUtf8() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "server.log", "text/plain", new byte[]{(byte) 0xC3, (byte) 0x28}
        );

        assertCode(() -> validator.validate(List.of(file)), "INVALID_LOG_ENCODING");
    }

    private MockMultipartFile file(String filename, String contentType, String content) {
        return new MockMultipartFile("file", filename, contentType, content.getBytes(StandardCharsets.UTF_8));
    }

    private void assertCode(Runnable action, String code) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(LogAnalysisException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(code));
    }
}
