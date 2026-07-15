package com.monit.pingbell.loganalysis.service;

import com.monit.pingbell.loganalysis.exception.LogAnalysisException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class LogFileValidator {

    static final long MAX_FILE_SIZE_BYTES = 5L * 1024 * 1024;
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "text/plain",
            "text/x-log",
            "application/octet-stream"
    );

    ValidatedLogFile validate(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw error("LOG_FILE_REQUIRED", "A log file is required.");
        }
        if (files.size() != 1) {
            throw error("UNSUPPORTED_LOG_FILE_TYPE", "Exactly one log file is allowed.");
        }

        MultipartFile file = files.getFirst();
        if (file == null || file.isEmpty()) {
            throw error("LOG_FILE_EMPTY", "The log file is empty.");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new LogAnalysisException(
                    HttpStatus.PAYLOAD_TOO_LARGE,
                    "LOG_FILE_TOO_LARGE",
                    "The log file must not exceed 5 MB."
            );
        }

        validateExtension(file.getOriginalFilename());
        validateContentType(file.getContentType());

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw error("INVALID_LOG_ENCODING", "The log file could not be read as UTF-8 text.");
        }

        if (hasBinarySignature(bytes) || containsBinaryControlBytes(bytes)) {
            throw error("UNSUPPORTED_LOG_FILE_TYPE", "Binary, compressed, and executable files are not allowed.");
        }

        String content = decodeUtf8(bytes);
        if (content.isBlank()) {
            throw error("LOG_FILE_EMPTY", "The log file is empty.");
        }
        return new ValidatedLogFile(content, bytes.length);
    }

    private void validateExtension(String filename) {
        String normalized = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        if (!normalized.endsWith(".log") && !normalized.endsWith(".txt")) {
            throw error("UNSUPPORTED_LOG_FILE_TYPE", "Only .log and .txt files are supported.");
        }
    }

    private void validateContentType(String contentType) {
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw error("UNSUPPORTED_LOG_FILE_TYPE", "The uploaded file must have a supported text MIME type.");
        }
    }

    private String decodeUtf8(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException e) {
            throw error("INVALID_LOG_ENCODING", "The log file could not be read as UTF-8 text.");
        }
    }

    private boolean hasBinarySignature(byte[] bytes) {
        return startsWith(bytes, 0x50, 0x4B)
                || startsWith(bytes, 0x1F, 0x8B)
                || startsWith(bytes, 0x4D, 0x5A)
                || startsWith(bytes, 0x7F, 0x45, 0x4C, 0x46)
                || startsWith(bytes, 0x25, 0x50, 0x44, 0x46);
    }

    private boolean startsWith(byte[] bytes, int... signature) {
        if (bytes.length < signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if ((bytes[i] & 0xFF) != signature[i]) {
                return false;
            }
        }
        return true;
    }

    private boolean containsBinaryControlBytes(byte[] bytes) {
        for (byte value : bytes) {
            int unsigned = value & 0xFF;
            if (unsigned == 0 || (unsigned < 0x09) || (unsigned > 0x0D && unsigned < 0x20)) {
                return true;
            }
        }
        return false;
    }

    private LogAnalysisException error(String code, String message) {
        return new LogAnalysisException(HttpStatus.BAD_REQUEST, code, message);
    }
}
