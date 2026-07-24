package com.monit.pingbell.runbook.service;

import com.monit.pingbell.runbook.exception.RunbookException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

@Component
public class RunbookContentValidator {
    private static final List<String> REQUIRED_SECTIONS = List.of(
            "증상", "영향", "확인 절차", "안전한 완화", "복구 검증", "재발 방지", "참고 링크");
    private static final List<Pattern> FORBIDDEN_PATTERNS = List.of(
            Pattern.compile("(?i)\\b(?:authorization|cookie|session(?:id)?|password|passwd|api[-_ ]?key|webhook[-_ ]?secret)\\s*[:=]\\s*\\S+"),
            Pattern.compile("(?i)\\bbearer\\s+[a-z0-9._~+/-]+=*"),
            Pattern.compile("\\beyJ[a-zA-Z0-9_-]{10,}\\.[a-zA-Z0-9_-]{10,}(?:\\.[a-zA-Z0-9_-]{10,})?\\b"),
            Pattern.compile("(?i)-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----"),
            Pattern.compile("(?i)\\b(?:jdbc:|postgres(?:ql)?://|mysql://|mongodb(?:\\+srv)?://)\\S+"),
            Pattern.compile("(?<![\\w.])(?:10|127)\\.(?:\\d{1,3}\\.){2}\\d{1,3}(?![\\w.])"),
            Pattern.compile("(?<![\\w.])192\\.168\\.(?:\\d{1,3}\\.)\\d{1,3}(?![\\w.])"),
            Pattern.compile("(?<![\\w.])172\\.(?:1[6-9]|2\\d|3[01])\\.(?:\\d{1,3}\\.)\\d{1,3}(?![\\w.])"),
            Pattern.compile("(?i)\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b"),
            Pattern.compile("(?<!\\d)(?:\\+?82[- ]?)?0?1[016789][- ]?\\d{3,4}[- ]?\\d{4}(?!\\d)"),
            Pattern.compile("(?i)(?<![a-z0-9.-])[a-z0-9.-]+\\.(?:internal|local)(?![a-z0-9.-])"),
            Pattern.compile("(?i)https?://(?![a-z0-9.-]*example\\.invalid(?:[/:?#]|$)|localhost(?:[/:?#]|$))[^\\s)]+")
    );

    public void validate(String title, String serviceName, List<String> errorTypes, String content) {
        String candidate = String.join("\n", title, serviceName, String.join("\n", errorTypes), content);
        if (FORBIDDEN_PATTERNS.stream().anyMatch(pattern -> pattern.matcher(candidate).find())) {
            throw new RunbookException(HttpStatus.BAD_REQUEST, "RUNBOOK_SENSITIVE_DATA_DETECTED",
                    "The runbook contains sensitive or production-specific information.");
        }
        int previous = -1;
        for (String section : REQUIRED_SECTIONS) {
            var matcher = Pattern.compile("(?m)^#{1,6}\\s+" + Pattern.quote(section) + "\\s*$").matcher(content);
            int current = matcher.find() ? matcher.start() : -1;
            if (current < 0 || current <= previous) {
                throw new RunbookException(HttpStatus.BAD_REQUEST, "RUNBOOK_STRUCTURE_INVALID",
                        "The runbook must contain all required sections in the specified order.");
            }
            previous = current;
        }
    }
}
