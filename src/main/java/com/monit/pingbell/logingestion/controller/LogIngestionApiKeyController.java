package com.monit.pingbell.logingestion.controller;

import com.monit.pingbell.global.security.auth.AuthenticatedMember;
import com.monit.pingbell.logingestion.dto.LogIngestionApiKeyIssueResponse;
import com.monit.pingbell.logingestion.dto.LogIngestionApiKeyResponse;
import com.monit.pingbell.logingestion.service.LogIngestionApiKeyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Issues/lists/revokes the long-lived API keys that Monitor-scoped log shippers (e.g. Fluent
 * Bit) use to authenticate against the log ingestion endpoint - see LogIngestionApiKeyService. */
@RestController
@RequestMapping("/api/monitors/{monitorId}/api-keys")
@RequiredArgsConstructor
public class LogIngestionApiKeyController {

    private final LogIngestionApiKeyService apiKeyService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LogIngestionApiKeyIssueResponse issue(
            @AuthenticationPrincipal AuthenticatedMember member,
            @PathVariable Long monitorId
    ) {
        return apiKeyService.issue(member.id(), monitorId);
    }

    @GetMapping
    public List<LogIngestionApiKeyResponse> list(
            @AuthenticationPrincipal AuthenticatedMember member,
            @PathVariable Long monitorId
    ) {
        return apiKeyService.list(member.id(), monitorId);
    }

    @DeleteMapping("/{keyId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(
            @AuthenticationPrincipal AuthenticatedMember member,
            @PathVariable Long monitorId,
            @PathVariable Long keyId
    ) {
        apiKeyService.revoke(member.id(), monitorId, keyId);
    }
}
