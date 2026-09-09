package com.monit.pingbell.runbook.controller;

import com.monit.pingbell.global.security.auth.AuthenticatedMember;
import com.monit.pingbell.runbook.dto.*;
import com.monit.pingbell.runbook.embedding.RunbookEmbeddingService;
import com.monit.pingbell.runbook.service.RunbookService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/runbooks")
@RequiredArgsConstructor
public class RunbookController {
    private final RunbookService runbookService;
    private final RunbookEmbeddingService runbookEmbeddingService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RunbookResponse create(@AuthenticationPrincipal AuthenticatedMember member,
                                  @Valid @RequestBody RunbookCreateRequest request) {
        return runbookService.create(member.id(), request);
    }

    @PostMapping("/{documentId}/versions")
    @ResponseStatus(HttpStatus.CREATED)
    public RunbookResponse createVersion(@AuthenticationPrincipal AuthenticatedMember member,
                                         @PathVariable String documentId,
                                         @Valid @RequestBody RunbookRevisionRequest request) {
        return runbookService.createVersion(member.id(), documentId, request);
    }

    @GetMapping
    public List<RunbookResponse> getActiveRunbooks(@AuthenticationPrincipal AuthenticatedMember member) {
        return runbookService.getActiveRunbooks(member.id());
    }

    @GetMapping("/{documentId}/versions/{version}")
    public RunbookResponse getRevision(@AuthenticationPrincipal AuthenticatedMember member,
                                       @PathVariable String documentId, @PathVariable int version) {
        return runbookService.getRevision(member.id(), documentId, version);
    }

    @PatchMapping("/{documentId}/versions/{version}/status")
    public RunbookResponse updateStatus(@AuthenticationPrincipal AuthenticatedMember member,
                                        @PathVariable String documentId, @PathVariable int version,
                                        @Valid @RequestBody RunbookStatusUpdateRequest request) {
        return runbookService.updateStatus(member.id(), documentId, version, request);
    }

    // Development/testing convenience: the create/activate flow does not yet trigger embedding
    // indexing automatically, so this lets an ACTIVE revision be manually (re)indexed for search.
    @PostMapping("/{documentId}/versions/{version}/reindex")
    public RunbookReindexResponse reindex(@AuthenticationPrincipal AuthenticatedMember member,
                                          @PathVariable String documentId, @PathVariable int version) {
        List<?> chunks = runbookEmbeddingService.reindex(member.id(), documentId, version);
        return new RunbookReindexResponse(chunks.size());
    }
}
