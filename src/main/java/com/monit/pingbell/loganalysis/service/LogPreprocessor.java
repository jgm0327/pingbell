package com.monit.pingbell.loganalysis.service;

import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.regex.Pattern;

/**
 * Masks well-known secret/PII shapes before log content ever reaches the ingestion buffer or an
 * external AI model (AGENTS.md §3). This is regex-based best-effort masking, not a general-purpose
 * PII scrubber - it can only catch shapes it was written to recognize, so it targets patterns
 * realistically expected across this operator's own projects: this app's own OpenAI/Slack/Discord
 * integrations, sibling side-projects that use GitHub/AWS/Google credentials, and common Korean
 * PII (phone numbers, resident registration numbers), plus generic card numbers. It will not catch
 * free-form personal data embedded in natural-language log text, or secret formats nobody's
 * projects here actually use - operators should still avoid logging sensitive data in the first
 * place rather than relying on this as a guarantee.
 */
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
    // Bare vendor-prefixed tokens, unlike SECRET above these need no "key=" label to be
    // recognized - the prefix itself is the signal. Covers services actually integrated across
    // this operator's own projects: OpenAI (pingbell itself), Slack/Discord (notification
    // channels), GitHub/AWS/Google (sibling side-projects' CI/cloud credentials).
    private static final Pattern VENDOR_SECRET = Pattern.compile(
            "(?:sk-[A-Za-z0-9_-]{20,}"
                    + "|xox[baprs]-[A-Za-z0-9-]{10,}"
                    + "|gh[pousr]_[A-Za-z0-9]{20,}"
                    + "|AKIA[0-9A-Z]{16}"
                    + "|AIza[0-9A-Za-z_-]{30,})"
    );
    private static final Pattern EMAIL = Pattern.compile(
            "(?i)(?<![A-Z0-9._%+-])[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}(?![A-Z0-9._%+-])"
    );
    private static final Pattern IPV4 = Pattern.compile(
            "(?<!\\d)(?:(?:25[0-5]|2[0-4]\\d|1?\\d?\\d)\\.){3}(?:25[0-5]|2[0-4]\\d|1?\\d?\\d)(?!\\d)"
    );
    // Korean resident registration number (주민등록번호): 6 digits, optional separator, a
    // gender/century digit (1-4), then 6 more digits. Checked before KR_PHONE/CARD_NUMBER since
    // its 13-digit shape is the most distinctive of the three.
    private static final Pattern KR_RRN = Pattern.compile("(?<!\\d)\\d{6}[- ]?[1-4]\\d{6}(?!\\d)");
    // Korean mobile phone number (010-1234-5678 and similar 01x formats).
    private static final Pattern KR_PHONE = Pattern.compile("(?<!\\d)01[016789][- .]?\\d{3,4}[- .]?\\d{4}(?!\\d)");
    // Generic card number: 16 digits in four groups of four, with or without separators.
    private static final Pattern CARD_NUMBER = Pattern.compile("(?<!\\d)(?:\\d{4}[- ]?){3}\\d{4}(?!\\d)");

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
        masked = VENDOR_SECRET.matcher(masked).replaceAll(MASK);
        masked = EMAIL.matcher(masked).replaceAll(MASK);
        masked = IPV4.matcher(masked).replaceAll(MASK);
        masked = KR_RRN.matcher(masked).replaceAll(MASK);
        masked = KR_PHONE.matcher(masked).replaceAll(MASK);
        return CARD_NUMBER.matcher(masked).replaceAll(MASK);
    }
}
