package com.monit.pingbell.runbook.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.monit.pingbell.global.security.jwt.JwtTokenProvider;
import com.monit.pingbell.runbook.domain.*;
import com.monit.pingbell.runbook.dto.RunbookCreateRequest;
import com.monit.pingbell.runbook.repository.*;
import com.monit.pingbell.runbook.service.RunbookContentValidatorTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;
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
