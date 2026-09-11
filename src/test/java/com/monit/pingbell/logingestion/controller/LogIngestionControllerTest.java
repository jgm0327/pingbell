package com.monit.pingbell.logingestion.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.monit.pingbell.logingestion.service.LogIngestionBufferService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LogIngestionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private LogIngestionBufferService bufferService;

    @Test
    void rejectsAHumanJwtInsteadOfAnIngestionApiKey() throws Exception {
        String jwtToken = signupAndGetToken("ingest-http-1@example.com");
        CreatedMonitor monitor = createMonitor(jwtToken, "svc-ing-1");

        mockMvc.perform(post("/api/v1/monitors/{monitorId}/logs", monitor.id())
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("hello"))
                .andExpect(status().isForbidden());
    }

    @Test
    void acceptsPlainTextMasksSensitiveLinesAndBuffersThem() throws Exception {
        String jwtToken = signupAndGetToken("ingest-http-2@example.com");
        CreatedMonitor monitor = createMonitor(jwtToken, "svc-ing-2");
        String apiKey = issueApiKey(jwtToken, monitor.id());

        mockMvc.perform(post("/api/v1/monitors/{monitorId}/logs", monitor.id())
                        .header("Authorization", "Bearer " + apiKey)
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("line one\npassword=hunter2\n\nline three"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.acceptedLines").value(3));

        // Redis (unlike the in-memory test DB) is a real, persistent service not reset between
        // separate test runs, so this only asserts the tail rather than exact buffer contents -
        // otherwise leftover entries from an earlier run reusing the same (tenantId, monitorId)
        // pair (H2 ids restart from 1 each run) would make this flaky.
        assertThat(bufferService.readAll(monitor.tenantId(), monitor.id()))
                .endsWith("line one", "password=[REDACTED]", "line three");
    }

    @Test
    void acceptsNdjsonExtractingLogOrMessageField() throws Exception {
        String jwtToken = signupAndGetToken("ingest-http-3@example.com");
        CreatedMonitor monitor = createMonitor(jwtToken, "svc-ing-3");
        String apiKey = issueApiKey(jwtToken, monitor.id());
        String body = "{\"log\":\"first\"}\n{\"message\":\"second\"}\n{\"other\":\"ignored\"}\n";

        mockMvc.perform(post("/api/v1/monitors/{monitorId}/logs", monitor.id())
                        .header("Authorization", "Bearer " + apiKey)
                        .contentType("application/x-ndjson")
                        .content(body))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.acceptedLines").value(2));
    }

    @Test
    void rejectsUnsupportedContentType() throws Exception {
        String jwtToken = signupAndGetToken("ingest-http-4@example.com");
        CreatedMonitor monitor = createMonitor(jwtToken, "svc-ing-4");
        String apiKey = issueApiKey(jwtToken, monitor.id());

        mockMvc.perform(post("/api/v1/monitors/{monitorId}/logs", monitor.id())
                        .header("Authorization", "Bearer " + apiKey)
                        .contentType(MediaType.APPLICATION_XML)
                        .content("<a/>"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("LOG_INGESTION_UNSUPPORTED_CONTENT_TYPE"));
    }

    @Test
    void rejectsAnApiKeyUsedAgainstADifferentMonitor() throws Exception {
        String jwtToken = signupAndGetToken("ingest-http-5@example.com");
        CreatedMonitor monitorA = createMonitor(jwtToken, "svc-ing-5a");
        CreatedMonitor monitorB = createMonitor(jwtToken, "svc-ing-5b");
        String apiKeyForA = issueApiKey(jwtToken, monitorA.id());

        mockMvc.perform(post("/api/v1/monitors/{monitorId}/logs", monitorB.id())
                        .header("Authorization", "Bearer " + apiKeyForA)
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("x"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("LOG_INGESTION_MONITOR_MISMATCH"));
    }

    @Test
    void rejectsOversizedPayload() throws Exception {
        String jwtToken = signupAndGetToken("ingest-http-6@example.com");
        CreatedMonitor monitor = createMonitor(jwtToken, "svc-ing-6");
        String apiKey = issueApiKey(jwtToken, monitor.id());
        String oversized = "a".repeat(600_000);

        mockMvc.perform(post("/api/v1/monitors/{monitorId}/logs", monitor.id())
                        .header("Authorization", "Bearer " + apiKey)
                        .contentType(MediaType.TEXT_PLAIN)
                        .content(oversized))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("LOG_INGESTION_PAYLOAD_TOO_LARGE"));
    }

    @Test
    void rejectsRequestsPastThePerMinuteRateLimit() throws Exception {
        String jwtToken = signupAndGetToken("ingest-http-7@example.com");
        CreatedMonitor monitor = createMonitor(jwtToken, "svc-ing-7");
        String apiKey = issueApiKey(jwtToken, monitor.id());

        for (int i = 0; i < 60; i++) {
            mockMvc.perform(post("/api/v1/monitors/{monitorId}/logs", monitor.id())
                            .header("Authorization", "Bearer " + apiKey)
                            .contentType(MediaType.TEXT_PLAIN)
                            .content("line " + i))
                    .andExpect(status().isAccepted());
        }

        mockMvc.perform(post("/api/v1/monitors/{monitorId}/logs", monitor.id())
                        .header("Authorization", "Bearer " + apiKey)
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("one too many"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("LOG_INGESTION_RATE_LIMITED"));
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

    private CreatedMonitor createMonitor(String token, String name) throws Exception {
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
        JsonNode created = objectMapper.readTree(result.getResponse().getContentAsString());
        return new CreatedMonitor(created.get("id").asLong(), created.get("userId").asLong());
    }

    private String issueApiKey(String token, long monitorId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/monitors/{monitorId}/api-keys", monitorId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("apiKey").asText();
    }

    private record CreatedMonitor(long id, long tenantId) {
    }
}
