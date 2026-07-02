package com.monit.pingbell.global.dlq;

import java.util.regex.Pattern;

final class DlqReasonRedactor {

    private static final Pattern URL_PATTERN = Pattern.compile("https?://\\S+", Pattern.CASE_INSENSITIVE);
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern JWT_PATTERN = Pattern.compile(
            "\\b[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}\\b"
    );
    private static final Pattern BEARER_PATTERN = Pattern.compile(
            "(?i)\\bBearer\\s+[A-Za-z0-9._~+/-]{16,}"
    );
    private static final Pattern SECRET_KEY_VALUE_PATTERN = Pattern.compile(
            "(?i)\\b(token|api[-_]?key|secret|password|authorization)\\s*([=:])\\s*[^\\s,;]+"
    );

    private DlqReasonRedactor() {
    }

    static String redact(String reason) {
        if (reason == null || reason.isBlank()) {
            return "";
        }

        String redacted = URL_PATTERN.matcher(reason).replaceAll("[REDACTED_URL]");
        redacted = EMAIL_PATTERN.matcher(redacted).replaceAll("[REDACTED_EMAIL]");
        redacted = JWT_PATTERN.matcher(redacted).replaceAll("[REDACTED_TOKEN]");
        redacted = BEARER_PATTERN.matcher(redacted).replaceAll("Bearer [REDACTED_TOKEN]");
        redacted = SECRET_KEY_VALUE_PATTERN.matcher(redacted).replaceAll("$1$2[REDACTED_SECRET]");
        return redacted;
    }
}
