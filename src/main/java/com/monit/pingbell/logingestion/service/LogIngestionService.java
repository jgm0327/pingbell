package com.monit.pingbell.logingestion.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.monit.pingbell.loganalysis.service.LogPreprocessor;
import com.monit.pingbell.logingestion.dto.LogIngestionAcceptedResponse;
import com.monit.pingbell.logingestion.exception.LogIngestionException;
import com.monit.pingbell.logingestion.config.LogIngestionProperties;
import com.monit.pingbell.logingestion.security.AuthenticatedIngestionKey;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Parses a batch of log lines from a collector (Fluent Bit's ndjson HTTP output, or a plain-text
 * shipper like a simple curl+cron script), masks them, and hands them to the short-lived buffer.
 * Never persists raw/unmasked content and never fails the caller's health-check/incident flow -
 * this is purely an ingestion path for Issue 3 to later read from.
 */
@Service
@RequiredArgsConstructor
public class LogIngestionService {

    private static final String NDJSON_CONTENT_TYPE = "application/x-ndjson";
    private static final String TEXT_CONTENT_TYPE = "text/plain";

    private final LogIngestionBufferService bufferService;
    private final LogIngestionProperties properties;
    private final LogPreprocessor logPreprocessor;
    private final ObjectMapper objectMapper;

    public LogIngestionAcceptedResponse ingest(
            AuthenticatedIngestionKey principal, Long monitorId, String contentType, String body
    ) {
        requireMatchingMonitor(principal, monitorId);
        requireWithinSizeLimit(body);
        requireWithinRateLimit(principal);

        List<String> lines = parseLines(contentType, body);
        List<String> masked = lines.stream()
                .map(logPreprocessor::mask)
                .filter(line -> line != null && !line.isBlank())
                .toList();

        bufferService.append(principal.tenantId(), principal.monitorId(), masked);
        return new LogIngestionAcceptedResponse(masked.size());
    }

    private void requireMatchingMonitor(AuthenticatedIngestionKey principal, Long monitorId) {
        if (!principal.monitorId().equals(monitorId)) {
            throw new LogIngestionException(HttpStatus.FORBIDDEN, "LOG_INGESTION_MONITOR_MISMATCH",
                    "This API key is not authorized for the requested Monitor.");
        }
    }

    private void requireWithinSizeLimit(String body) {
        int bytes = body == null ? 0 : body.getBytes(StandardCharsets.UTF_8).length;
        if (bytes > properties.getMaxRequestBytes()) {
            throw new LogIngestionException(HttpStatus.PAYLOAD_TOO_LARGE, "LOG_INGESTION_PAYLOAD_TOO_LARGE",
                    "The log payload must not exceed " + properties.getMaxRequestBytes() + " bytes.");
        }
    }

    private void requireWithinRateLimit(AuthenticatedIngestionKey principal) {
        if (!bufferService.tryConsumeRateLimit(principal.tenantId(), principal.monitorId())) {
            throw new LogIngestionException(HttpStatus.TOO_MANY_REQUESTS, "LOG_INGESTION_RATE_LIMITED",
                    "Too many log ingestion requests for this Monitor.");
        }
    }

    private List<String> parseLines(String contentType, String body) {
        if (body == null || body.isBlank()) {
            return List.of();
        }
        String normalized = normalizeContentType(contentType);
        if (NDJSON_CONTENT_TYPE.equals(normalized)) {
            return parseNdjson(body);
        }
        if (normalized == null || TEXT_CONTENT_TYPE.equals(normalized)) {
            return parsePlainText(body);
        }
        throw new LogIngestionException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "LOG_INGESTION_UNSUPPORTED_CONTENT_TYPE",
                "Only application/x-ndjson and text/plain are supported.");
    }

    private String normalizeContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return null;
        }
        int separator = contentType.indexOf(';');
        String base = separator >= 0 ? contentType.substring(0, separator) : contentType;
        return base.strip().toLowerCase(Locale.ROOT);
    }

    private List<String> parsePlainText(String body) {
        return body.lines().map(String::strip).filter(line -> !line.isEmpty()).toList();
    }

    private List<String> parseNdjson(String body) {
        List<String> lines = new ArrayList<>();
        for (String raw : body.lines().toList()) {
            if (raw.isBlank()) {
                continue;
            }
            try {
                String text = extractText(objectMapper.readTree(raw));
                if (text != null && !text.isBlank()) {
                    lines.add(text);
                }
            } catch (JsonProcessingException e) {
                // A malformed line from a misconfigured or partially-written collector batch -
                // skip just that line rather than failing the whole batch.
            }
        }
        return lines;
    }

    private String extractText(JsonNode node) {
        if (node.hasNonNull("log")) {
            return node.get("log").asText();
        }
        if (node.hasNonNull("message")) {
            return node.get("message").asText();
        }
        return null;
    }
}
