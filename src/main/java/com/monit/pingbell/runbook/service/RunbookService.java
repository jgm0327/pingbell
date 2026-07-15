package com.monit.pingbell.runbook.service;

import com.monit.pingbell.runbook.domain.*;
import com.monit.pingbell.runbook.dto.*;
import com.monit.pingbell.runbook.exception.RunbookException;
import com.monit.pingbell.runbook.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RunbookService {
    private final RunbookDocumentRepository documentRepository;
    private final RunbookRevisionRepository revisionRepository;
    private final RunbookContentValidator contentValidator;
    private final Clock clock;

    @Transactional
    public RunbookResponse create(Long memberId, RunbookCreateRequest request) {
        validateCreatableStatus(request.status());
        contentValidator.validate(request.title(), request.serviceName(), request.errorTypes(), request.content());
        if (documentRepository.existsByTenantIdAndDocumentId(memberId, request.documentId())) {
            throw conflict("RUNBOOK_DOCUMENT_ALREADY_EXISTS", "The documentId already exists in this tenant.");
        }
        RunbookDocument document = documentRepository.saveAndFlush(
                new RunbookDocument(memberId, memberId, request.documentId()));
        return RunbookResponse.from(saveRevision(document, 1, request.revision()));
    }

    @Transactional
    public RunbookResponse createVersion(Long memberId, String documentId, RunbookRevisionRequest request) {
        validateCreatableStatus(request.status());
        contentValidator.validate(request.title(), request.serviceName(), request.errorTypes(), request.content());
        RunbookDocument document = getOwnedDocumentForUpdate(memberId, documentId);
        int nextVersion = revisionRepository.findFirstByDocumentIdOrderByVersionDesc(document.getId())
                .map(revision -> revision.getVersion() + 1)
                .orElse(1);
        return RunbookResponse.from(saveRevision(document, nextVersion, request));
    }

    @Transactional(readOnly = true)
    public List<RunbookResponse> getActiveRunbooks(Long memberId) {
        return revisionRepository.findAllByDocumentTenantIdAndStatusOrderByUpdatedAtDesc(memberId, RunbookStatus.ACTIVE)
                .stream().map(RunbookResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public RunbookResponse getRevision(Long memberId, String documentId, int version) {
        return RunbookResponse.from(revisionRepository
                .findByDocumentTenantIdAndDocumentDocumentIdAndVersion(memberId, documentId, version)
                .orElseThrow(this::notFound));
    }

    @Transactional
    public RunbookResponse updateStatus(Long memberId, String documentId, int version,
                                        RunbookStatusUpdateRequest request) {
        if (request.status() != RunbookStatus.ACTIVE && request.status() != RunbookStatus.RETIRED) {
            throw invalidTransition("Only ACTIVE or RETIRED can be requested explicitly.");
        }
        RunbookDocument document = getOwnedDocumentForUpdate(memberId, documentId);
        RunbookRevision revision = revisionRepository
                .findByDocumentTenantIdAndDocumentDocumentIdAndVersion(memberId, documentId, version)
                .orElseThrow(this::notFound);
        try {
            if (request.status() == RunbookStatus.ACTIVE) {
                RunbookRevision latest = revisionRepository.findFirstByDocumentIdOrderByVersionDesc(document.getId())
                        .orElseThrow(this::notFound);
                if (!latest.getId().equals(revision.getId())) {
                    throw invalidTransition("Only the latest revision can be activated.");
                }
                supersedeCurrentActive(document, revision);
                revision.activate();
            } else {
                revision.retire();
            }
        } catch (IllegalStateException e) {
            throw invalidTransition(e.getMessage());
        }
        return RunbookResponse.from(revision);
    }

    private RunbookRevision saveRevision(RunbookDocument document, int version, RunbookRevisionRequest request) {
        if (request.status() == RunbookStatus.ACTIVE) {
            supersedeCurrentActive(document, null);
        }
        return revisionRepository.save(new RunbookRevision(
                document, request.title(), request.documentType(), request.serviceName(), request.errorTypes(),
                version, request.status(), request.content(), Instant.now(clock)));
    }

    private void supersedeCurrentActive(RunbookDocument document, RunbookRevision activating) {
        revisionRepository.findByDocumentIdAndStatus(document.getId(), RunbookStatus.ACTIVE)
                .filter(active -> activating == null || !active.getId().equals(activating.getId()))
                .ifPresent(RunbookRevision::supersede);
    }

    private RunbookDocument getOwnedDocumentForUpdate(Long memberId, String documentId) {
        return documentRepository.findOwnedForUpdate(memberId, documentId).orElseThrow(this::notFound);
    }

    private void validateCreatableStatus(RunbookStatus status) {
        if (status != RunbookStatus.DRAFT && status != RunbookStatus.ACTIVE) {
            throw invalidTransition("A new revision must start as DRAFT or ACTIVE.");
        }
    }

    private RunbookException notFound() {
        return new RunbookException(HttpStatus.NOT_FOUND, "RUNBOOK_NOT_FOUND", "Runbook not found.");
    }

    private RunbookException conflict(String code, String message) {
        return new RunbookException(HttpStatus.CONFLICT, code, message);
    }

    private RunbookException invalidTransition(String message) {
        return conflict("RUNBOOK_STATUS_TRANSITION_INVALID", message);
    }
}
