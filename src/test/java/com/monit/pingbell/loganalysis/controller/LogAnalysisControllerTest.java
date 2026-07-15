package com.monit.pingbell.loganalysis.controller;

import com.monit.pingbell.global.security.jwt.JwtTokenProvider;
import com.monit.pingbell.loganalysis.dto.Confidence;
import com.monit.pingbell.loganalysis.dto.LogAnalysisResponse;
import com.monit.pingbell.loganalysis.dto.RecommendedActionResponse;
import com.monit.pingbell.loganalysis.dto.SuspectedCauseResponse;
import com.monit.pingbell.loganalysis.exception.LogAnalysisException;
import com.monit.pingbell.loganalysis.service.LogAnalysisService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LogAnalysisControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private LogAnalysisService logAnalysisService;

    @Test
    void requiresJwtAuthentication() throws Exception {
        mockMvc.perform(multipart("/api/v1/monitors/{monitorId}/log-analyses", 12L)
                        .file(file()))
                .andExpect(status().isForbidden());
    }

    @Test
    void acceptsMultipartRequestAndReturnsContract() throws Exception {
        when(logAnalysisService.analyze(eq(1L), eq(12L), any())).thenReturn(response());
        String token = jwtTokenProvider.createAccessToken(1L, "user@example.com");

        mockMvc.perform(multipart("/api/v1/monitors/{monitorId}/log-analyses", 12L)
                        .file(file())
                        .param("question", "Why did this fail?")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.monitorId").value(12))
                .andExpect(jsonPath("$.summary").value("Possible DB timeout."))
                .andExpect(jsonPath("$.suspectedCauses[0].confidence").value("HIGH"))
                .andExpect(jsonPath("$.recommendedActions[0].priority").value(1))
                .andExpect(jsonPath("$.truncated").value(false))
                .andExpect(jsonPath("$.originalSizeBytes").value(42))
                .andExpect(jsonPath("$.analyzedCharacters").value(42));
    }

    @Test
    void returnsCommonErrorResponseWithoutSensitiveInput() throws Exception {
        when(logAnalysisService.analyze(eq(1L), eq(12L), any())).thenThrow(
                new LogAnalysisException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "LOG_ANALYSIS_AI_UNAVAILABLE",
                        "The AI analysis service is unavailable."
                )
        );
        String token = jwtTokenProvider.createAccessToken(1L, "user@example.com");

        mockMvc.perform(multipart("/api/v1/monitors/{monitorId}/log-analyses", 12L)
                        .file(file())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("LOG_ANALYSIS_AI_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value("The AI analysis service is unavailable."))
                .andExpect(jsonPath("$.path").value("/api/v1/monitors/12/log-analyses"));
    }

    private MockMultipartFile file() {
        return new MockMultipartFile("file", "server.log", "text/plain", "error".getBytes());
    }

    private LogAnalysisResponse response() {
        return new LogAnalysisResponse(
                12L,
                "Possible DB timeout.",
                List.of(new SuspectedCauseResponse("Pool exhaustion", Confidence.HIGH, "Timeout repeated.")),
                List.of(new RecommendedActionResponse(1, "Inspect pool usage.", null)),
                List.of("connection timeout"),
                List.of("Additional checks are required."),
                false,
                42,
                42
        );
    }
}
