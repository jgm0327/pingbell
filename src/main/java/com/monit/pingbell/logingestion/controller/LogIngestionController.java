package com.monit.pingbell.logingestion.controller;

import com.monit.pingbell.logingestion.dto.LogIngestionAcceptedResponse;
import com.monit.pingbell.logingestion.security.AuthenticatedIngestionKey;
import com.monit.pingbell.logingestion.service.LogIngestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Where a Monitor-scoped log shipper (Fluent Bit, or a plain curl script) pushes recent log
 * lines. Authenticated with a log ingestion API key only - see LogIngestionApiKeyController and
 * LogIngestionApiKeyAuthenticationFilter. */
@RestController
@RequestMapping("/api/v1/monitors/{monitorId}/logs")
@RequiredArgsConstructor
public class LogIngestionController {

    private final LogIngestionService logIngestionService;

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public LogIngestionAcceptedResponse ingest(
            @AuthenticationPrincipal AuthenticatedIngestionKey principal,
            @PathVariable Long monitorId,
            @RequestHeader(value = HttpHeaders.CONTENT_TYPE, required = false) String contentType,
            @RequestBody(required = false) String body
    ) {
        return logIngestionService.ingest(principal, monitorId, contentType, body);
    }
}
