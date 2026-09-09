package com.monit.pingbell.logingestion.dto;

import java.time.LocalDateTime;

/** The raw {@code apiKey} value is only ever returned here, at issuance time. It cannot be
 * retrieved again afterward - only its {@code keyPrefix} shows up in later listings.
 * {@code fluentBitConfig}/{@code dockerComposeSnippet} are generated from that same one-time
 * key, so they too are only ever available in this response - see
 * LogIngestionConfigTemplateService. The only value left for the user to fill in is their own
 * log file path. */
public record LogIngestionApiKeyIssueResponse(
        Long id,
        String apiKey,
        String keyPrefix,
        LocalDateTime createdAt,
        String fluentBitConfig,
        String dockerComposeSnippet
) {
}
