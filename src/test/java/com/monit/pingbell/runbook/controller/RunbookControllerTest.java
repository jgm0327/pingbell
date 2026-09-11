package com.monit.pingbell.runbook.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.monit.pingbell.global.security.jwt.JwtTokenProvider;
import com.monit.pingbell.runbook.domain.*;
import com.monit.pingbell.runbook.dto.RunbookCreateRequest;
import com.monit.pingbell.runbook.embedding.RunbookChunk;
import com.monit.pingbell.runbook.embedding.RunbookEmbeddingException;
import com.monit.pingbell.runbook.embedding.RunbookEmbeddingService;
import com.monit.pingbell.runbook.repository.*;
import com.monit.pingbell.runbook.service.RunbookContentValidatorTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RunbookControllerTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JwtTokenProvider jwtTokenProvider;
    @Autowired RunbookRevisionRepository revisionRepository;
    @Autowired RunbookDocumentRepository documentRepository;

    // The real bean is backed by pgvector-specific SQL that the H2-based test profile cannot run,
    // so the reindex endpoint is verified against a mock instead of the full embedding pipeline.
    @MockitoBean RunbookEmbeddingService runbookEmbeddingService;

    @BeforeEach
    void cleanUp() {
        revisionRepository.deleteAll();
        documentRepository.deleteAll();
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/runbooks")).andExpect(status().isForbidden());
    }

    @Test
    void tenantAndOwnerAreDerivedFromAuthentication() throws Exception {
        mockMvc.perform(post("/api/runbooks")
                        .header("Authorization", bearer(7L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request("owned", RunbookContentValidatorTest.validContent()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.documentId").value("owned"))
                .andExpect(jsonPath("$.tenantId").value("7"))
                .andExpect(jsonPath("$.ownerId").value("7"))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void anotherTenantGetsNotFoundForDirectIdAccess() throws Exception {
        createAs(1L, "hidden");
        mockMvc.perform(get("/api/runbooks/{documentId}/versions/{version}", "hidden", 1)
                        .header("Authorization", bearer(2L)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RUNBOOK_NOT_FOUND"));

        mockMvc.perform(patch("/api/runbooks/{documentId}/versions/{version}/status", "hidden", 1)
                        .header("Authorization", bearer(2L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"RETIRED\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RUNBOOK_NOT_FOUND"));
    }

    @Test
    void returnsCommonErrorForSensitiveContent() throws Exception {
        String content = RunbookContentValidatorTest.validContent() + "\npassword=real-secret";
        mockMvc.perform(post("/api/runbooks")
                        .header("Authorization", bearer(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request("sensitive", content))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RUNBOOK_SENSITIVE_DATA_DETECTED"))
                .andExpect(jsonPath("$.message").value(
                        "The runbook contains sensitive or production-specific information."));
    }

    @Test
    void validatesDtoAndEnumContract() throws Exception {
        String invalid = """
                {"documentId":"invalid","title":"","documentType":"POSTMORTEM",
                 "serviceName":"Order API","errorTypes":[],"status":"ACTIVE","content":"x"}
                """;
        mockMvc.perform(post("/api/runbooks")
                        .header("Authorization", bearer(1L))
                        .contentType(MediaType.APPLICATION_JSON).content(invalid))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void reindexReturnsChunkCountForTheAuthenticatedTenant() throws Exception {
        when(runbookEmbeddingService.reindex(7L, "owned", 1)).thenReturn(List.of(
                chunk("owned", "chunk-1"), chunk("owned", "chunk-2")));

        mockMvc.perform(post("/api/runbooks/{documentId}/versions/{version}/reindex", "owned", 1)
                        .header("Authorization", bearer(7L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chunkCount").value(2));
    }

    @Test
    void reindexMapsEmbeddingFailureToBadGateway() throws Exception {
        when(runbookEmbeddingService.reindex(eq(7L), eq("owned"), eq(1)))
                .thenThrow(new RunbookEmbeddingException("EMBEDDING_CLIENT_FAILED", "The embedding client failed."));

        mockMvc.perform(post("/api/runbooks/{documentId}/versions/{version}/reindex", "owned", 1)
                        .header("Authorization", bearer(7L)))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("EMBEDDING_CLIENT_FAILED"));
    }

    @Test
    void reindexMapsMissingActiveRevisionToNotFound() throws Exception {
        when(runbookEmbeddingService.reindex(eq(7L), eq("missing"), eq(1)))
                .thenThrow(new RunbookEmbeddingException(
                        "RUNBOOK_ACTIVE_REVISION_NOT_FOUND", "The tenant-owned active Runbook revision was not found."));

        mockMvc.perform(post("/api/runbooks/{documentId}/versions/{version}/reindex", "missing", 1)
                        .header("Authorization", bearer(7L)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RUNBOOK_ACTIVE_REVISION_NOT_FOUND"));
    }

    @Test
    void reindexRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/runbooks/{documentId}/versions/{version}/reindex", "owned", 1))
                .andExpect(status().isForbidden());
    }

    private RunbookChunk chunk(String documentId, String chunkId) {
        return new RunbookChunk(7L, documentId, 1, chunkId, 0, "확인", "content", "hash-" + chunkId);
    }

    private void createAs(Long memberId, String documentId) throws Exception {
        mockMvc.perform(post("/api/runbooks")
                        .header("Authorization", bearer(memberId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                request(documentId, RunbookContentValidatorTest.validContent()))))
                .andExpect(status().isCreated());
    }

    private RunbookCreateRequest request(String id, String content) {
        return new RunbookCreateRequest(id, "합성 timeout 대응", RunbookDocumentType.RUNBOOK,
                "order-api", List.of("TIMEOUT"), RunbookStatus.ACTIVE, content);
    }

    private String bearer(Long memberId) {
        return "Bearer " + jwtTokenProvider.createAccessToken(memberId, "synthetic@example.invalid");
    }
}
