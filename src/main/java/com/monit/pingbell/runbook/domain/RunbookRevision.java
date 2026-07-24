package com.monit.pingbell.runbook.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "runbook_revisions", uniqueConstraints =
        @UniqueConstraint(name = "uk_runbook_revision_document_version", columnNames = {"runbook_document_id", "version"}))
public class RunbookRevision {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "runbook_document_id", nullable = false, updatable = false,
            foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private RunbookDocument document;

    @Column(nullable = false, updatable = false, length = 200)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, updatable = false, length = 30)
    private RunbookDocumentType documentType;

    @Column(name = "service_name", nullable = false, updatable = false, length = 100)
    private String serviceName;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "runbook_revision_error_types",
            joinColumns = @JoinColumn(name = "runbook_revision_id", foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT)))
    @OrderColumn(name = "sort_order")
    @Column(name = "error_type", nullable = false, length = 100)
    private List<String> errorTypes = new ArrayList<>();

    @Column(nullable = false, updatable = false)
    private Integer version;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RunbookStatus status;

    @Column(nullable = false, updatable = false, columnDefinition = "text")
    private String content;

    @Column(name = "updated_at", nullable = false, updatable = false)
    private Instant updatedAt;

    public RunbookRevision(RunbookDocument document, String title, RunbookDocumentType documentType,
                           String serviceName, List<String> errorTypes, Integer version,
                           RunbookStatus status, String content, Instant updatedAt) {
        this.document = document;
        this.title = title;
        this.documentType = documentType;
        this.serviceName = serviceName;
        this.errorTypes = new ArrayList<>(errorTypes);
        this.version = version;
        this.status = status;
        this.content = content;
        this.updatedAt = updatedAt;
    }

    public void activate() {
        requireStatus(RunbookStatus.DRAFT, "Only a DRAFT revision can be activated.");
        status = RunbookStatus.ACTIVE;
    }

    public void supersede() {
        requireStatus(RunbookStatus.ACTIVE, "Only an ACTIVE revision can be superseded.");
        status = RunbookStatus.SUPERSEDED;
    }

    public void retire() {
        if (status != RunbookStatus.DRAFT && status != RunbookStatus.ACTIVE) {
            throw new IllegalStateException("Only a DRAFT or ACTIVE revision can be retired.");
        }
        status = RunbookStatus.RETIRED;
    }

    private void requireStatus(RunbookStatus required, String message) {
        if (status != required) {
            throw new IllegalStateException(message);
        }
    }
}
