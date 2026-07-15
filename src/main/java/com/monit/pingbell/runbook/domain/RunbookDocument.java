package com.monit.pingbell.runbook.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "runbook_documents", uniqueConstraints =
        @UniqueConstraint(name = "uk_runbook_document_tenant_document", columnNames = {"tenant_id", "document_id"}))
public class RunbookDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private Long tenantId;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private Long ownerId;

    @Column(name = "document_id", nullable = false, updatable = false, length = 100)
    private String documentId;

    public RunbookDocument(Long tenantId, Long ownerId, String documentId) {
        this.tenantId = tenantId;
        this.ownerId = ownerId;
        this.documentId = documentId;
    }
}
