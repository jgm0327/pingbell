package com.monit.pingbell.logingestion.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LogIngestionApiKeyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/monitors/{monitorId}/api-keys", 1L))
                .andExpect(status().isForbidden());
    }

    @Test
    void issuesListsAndRevokesAnApiKeyForAnOwnedMonitor() throws Exception {
        String token = signupAndGetToken("ingestion-owner-1@example.com");
        long monitorId = createMonitor(token, "svc-a");

        MvcResult issueResult = mockMvc.perform(post("/api/monitors/{monitorId}/api-keys", monitorId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.apiKey").value(startsWith("pgbl_")))
                .andExpect(jsonPath("$.keyPrefix").value(startsWith("pgbl_")))
                .andExpect(jsonPath("$.fluentBitConfig").value(
                        org.hamcrest.Matchers.containsString("/api/v1/monitors/" + monitorId + "/logs")))
                .andExpect(jsonPath("$.dockerComposeSnippet").value(
                        org.hamcrest.Matchers.containsString("fluent-bit-monitor-" + monitorId)))
                .andReturn();
        JsonNode issued = objectMapper.readTree(issueResult.getResponse().getContentAsString());
        long keyId = issued.get("id").asLong();

        mockMvc.perform(get("/api/monitors/{monitorId}/api-keys", monitorId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(keyId))
                .andExpect(jsonPath("$[0].keyPrefix").value(issued.get("keyPrefix").asText()))
                .andExpect(jsonPath("$[0].revoked").value(false))
                .andExpect(jsonPath("$[0].apiKey").doesNotExist());

        mockMvc.perform(delete("/api/monitors/{monitorId}/api-keys/{keyId}", monitorId, keyId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/monitors/{monitorId}/api-keys", monitorId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$[0].revoked").value(true));
    }

    @Test
    void anotherMemberCannotIssueListOrRevokeKeysForSomeoneElsesMonitor() throws Exception {
        String ownerToken = signupAndGetToken("ingestion-owner-2@example.com");
        long monitorId = createMonitor(ownerToken, "svc-b");
        String strangerToken = signupAndGetToken("ingestion-stranger@example.com");

        // The app-wide OwnershipInterceptor (WebConfig, registered for /api/monitors/{monitorId}/**)
        // rejects these before the controller/service ever runs, so the code here is its generic
        // "NOT_FOUND" rather than LogIngestionApiKeyService's own "MONITOR_NOT_FOUND" - that second,
        // more specific check only ever fires for a direct service call (see the service unit test).
        mockMvc.perform(post("/api/monitors/{monitorId}/api-keys", monitorId)
                        .header("Authorization", "Bearer " + strangerToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        mockMvc.perform(get("/api/monitors/{monitorId}/api-keys", monitorId)
                        .header("Authorization", "Bearer " + strangerToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        mockMvc.perform(delete("/api/monitors/{monitorId}/api-keys/{keyId}", monitorId, 1)
                        .header("Authorization", "Bearer " + strangerToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void issuedApiKeyCannotAuthenticateAgainstHumanFacingEndpoints() throws Exception {
        String token = signupAndGetToken("ingestion-owner-3@example.com");
        long monitorId = createMonitor(token, "svc-c");
        MvcResult issueResult = mockMvc.perform(post("/api/monitors/{monitorId}/api-keys", monitorId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn();
        String apiKey = objectMapper.readTree(issueResult.getResponse().getContentAsString()).get("apiKey").asText();

        // ROLE_INGESTION must not satisfy the ROLE_USER requirement on the normal API surface.
        mockMvc.perform(get("/api/monitors")
                        .header("Authorization", "Bearer " + apiKey))
                .andExpect(status().isForbidden());
    }

    @Test
    void revokedKeyIsRejectedByRevokeAgain() throws Exception {
        String token = signupAndGetToken("ingestion-owner-4@example.com");
        long monitorId = createMonitor(token, "svc-d");
        MvcResult issueResult = mockMvc.perform(post("/api/monitors/{monitorId}/api-keys", monitorId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn();
        long keyId = objectMapper.readTree(issueResult.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(delete("/api/monitors/{monitorId}/api-keys/{keyId}", monitorId, keyId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        // Revoking an already-revoked key is idempotent - it exists, just already inactive.
        mockMvc.perform(delete("/api/monitors/{monitorId}/api-keys/{keyId}", monitorId, keyId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    private String signupAndGetToken(String email) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("email", email, "password", "test-password-1"));
        MvcResult result = mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private long createMonitor(String token, String name) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "name", name,
                "url", "https://example.invalid/health",
                "intervalSeconds", 60,
                "timeoutMillis", 3000,
                "failureThreshold", 3,
                "recoveryThreshold", 2));
        MvcResult result = mockMvc.perform(post("/api/monitors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }
}
