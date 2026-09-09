package com.monit.pingbell.loganalysis.service;

import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.regex.Pattern;

@Component
public class LogPreprocessor {

    static final int MAX_ANALYZED_CHARACTERS = 100_000;
    static final int MAX_ANALYZED_LINES = 2_000;
    private static final String MASK = "[REDACTED]";

    private static final Pattern AUTHORIZATION = Pattern.compile(
            "(?i)(\"?authorization\"?\\s*[:=]\\s*\"?(?:bearer\\s+)?)[^\"\\s,;}]+"
    );
    private static final Pattern BEARER = Pattern.compile("(?i)(bearer\\s+)[A-Za-z0-9._~+/=-]+");
    private static final Pattern JWT = Pattern.compile(
            "(?<![A-Za-z0-9_-])[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}(?![A-Za-z0-9_-])"
    );
    private static final Pattern SECRET = Pattern.compile(
            "(?i)(\"?(?:password|passwd|pwd|secret|api[ _-]?key|access[ _-]?key)\"?\\s*[:=]\\s*\"?)[^\"\\s,;}]+"
    );
    private static final Pattern EMAIL = Pattern.compile(
            "(?i)(?<![A-Z0-9._%+-])[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}(?![A-Z0-9._%+-])"
    );
    private static final Pattern IPV4 = Pattern.compile(
            "(?<!\\d)(?:(?:25[0-5]|2[0-4]\\d|1?\\d?\\d)\\.){3}(?:25[0-5]|2[0-4]\\d|1?\\d?\\d)(?!\\d)"
    );

    ProcessedLog process(String rawLog) {
        String masked = mask(rawLog);
        String[] lines = masked.split("\\R", -1);
        boolean truncated = lines.length > MAX_ANALYZED_LINES;

        String selected = masked;
        if (truncated) {
            selected = String.join("\n", Arrays.copyOfRange(lines, lines.length - MAX_ANALYZED_LINES, lines.length));
        }
        if (selected.length() > MAX_ANALYZED_CHARACTERS) {
            selected = selected.substring(selected.length() - MAX_ANALYZED_CHARACTERS);
            truncated = true;
        }
        return new ProcessedLog(selected, truncated, selected.length());
    }

    // Public: also called from com.monit.pingbell.logingestion to mask log lines before they
    // ever reach the short-lived ingestion buffer.
    public String mask(String value) {
        if (value == null) {
            return null;
        }
        String masked = AUTHORIZATION.matcher(value).replaceAll("$1" + MASK);
        masked = BEARER.matcher(masked).replaceAll("$1" + MASK);
        masked = JWT.matcher(masked).replaceAll(MASK);
        masked = SECRET.matcher(masked).replaceAll("$1" + MASK);
        masked = EMAIL.matcher(masked).replaceAll(MASK);
        return IPV4.matcher(masked).replaceAll(MASK);
    }
}
