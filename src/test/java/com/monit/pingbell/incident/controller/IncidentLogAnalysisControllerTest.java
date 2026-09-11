package com.monit.pingbell.incident.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.monit.pingbell.incident.domain.Incident;
import com.monit.pingbell.incident.domain.IncidentLogAnalysis;
import com.monit.pingbell.incident.domain.IncidentStatus;
import com.monit.pingbell.incident.repository.IncidentLogAnalysisRepository;
import com.monit.pingbell.incident.repository.IncidentRepository;
import com.monit.pingbell.loganalysis.dto.LogAnalysisResponse;
import com.monit.pingbell.monitor.domain.Monitor;
import com.monit.pingbell.monitor.repository.MonitorRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class IncidentLogAnalysisControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MonitorRepository monitorRepository;

    @Autowired
    private IncidentRepository incidentRepository;

    @Autowired
    private IncidentLogAnalysisRepository incidentLogAnalysisRepository;

    @Test
    void returnsCompletedAnalysisForAnOwnedIncident() throws Exception {
        String token = signupAndGetToken("incident-la-1@example.com");
        long monitorId = createMonitor(token, "svc-la-1");
        Incident incident = openIncident(monitorId);
        saveCompletedAnalysis(incident.getId(), monitorId);

        mockMvc.perform(get("/api/incidents/{incidentId}/log-analysis", incident.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incidentId").value(incident.getId()))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.result.summary").value("Automatic summary"))
                .andExpect(jsonPath("$.errorMessage").doesNotExist());
    }

    @Test
    void returnsNotFoundWhenNoAutomaticAnalysisExistsYet() throws Exception {
        String token = signupAndGetToken("incident-la-2@example.com");
        long monitorId = createMonitor(token, "svc-la-2");
        Incident incident = openIncident(monitorId);

        mockMvc.perform(get("/api/incidents/{incidentId}/log-analysis", incident.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void anotherMemberCannotReadSomeoneElsesIncidentLogAnalysis() throws Exception {
        String ownerToken = signupAndGetToken("incident-la-3-owner@example.com");
        long monitorId = createMonitor(ownerToken, "svc-la-3");
        Incident incident = openIncident(monitorId);
        saveCompletedAnalysis(incident.getId(), monitorId);
        String strangerToken = signupAndGetToken("incident-la-3-stranger@example.com");

        mockMvc.perform(get("/api/incidents/{incidentId}/log-analysis", incident.getId())
                        .header("Authorization", "Bearer " + strangerToken))
                .andExpect(status().isNotFound());
    }

    private Incident openIncident(long monitorId) {
        Monitor monitor = monitorRepository.findById(monitorId).orElseThrow();
        return incidentRepository.save(Incident.builder()
                .monitor(monitor)
                .status(IncidentStatus.OPEN)
                .startedAt(LocalDateTime.now())
                .lastErrorMessage("HTTP 500")
                .build());
    }

    private void saveCompletedAnalysis(Long incidentId, long monitorId) throws Exception {
        IncidentLogAnalysis analysis = IncidentLogAnalysis.processing(incidentId, LocalDateTime.now());
        LogAnalysisResponse response = new LogAnalysisResponse(
                monitorId, "Automatic summary", List.of(), List.of(), List.of(), List.of("warning"),
                List.of(), false, 42, 42);
        analysis.complete(objectMapper.writeValueAsString(response), LocalDateTime.now());
        incidentLogAnalysisRepository.save(analysis);
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
        JsonNode created = objectMapper.readTree(result.getResponse().getContentAsString());
        return created.get("id").asLong();
    }
}
