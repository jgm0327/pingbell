package com.monit.pingbell.logingestion.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.monit.pingbell.loganalysis.service.LogPreprocessor;
import com.monit.pingbell.logingestion.config.LogIngestionProperties;
import com.monit.pingbell.logingestion.dto.LogIngestionAcceptedResponse;
import com.monit.pingbell.logingestion.exception.LogIngestionException;
import com.monit.pingbell.logingestion.security.AuthenticatedIngestionKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LogIngestionServiceTest {

    @Mock
    private LogIngestionBufferService bufferService;

    private LogIngestionProperties properties;
    private LogIngestionService service;
    private final AuthenticatedIngestionKey principal = new AuthenticatedIngestionKey(12L, 1L);

    @BeforeEach
    void setUp() {
        properties = new LogIngestionProperties();
        service = new LogIngestionService(bufferService, properties, new LogPreprocessor(), new ObjectMapper());
    }

    /** Tests that reach the buffer append step need the rate limit to pass; tests that throw
     * before that point (monitor mismatch, oversized payload) must NOT stub this, or Mockito's
     * strict per-test stubbing check flags it as unused. */
    private void allowRateLimit() {
        when(bufferService.tryConsumeRateLimit(1L, 12L)).thenReturn(true);
    }

    @Test
    void plainTextSplitsStripsAndDropsBlankLines() {
        allowRateLimit();
        LogIngestionAcceptedResponse response =
                service.ingest(principal, 12L, "text/plain", "  line one  \n\npassword=hunter2\nline three");

        ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
        verify(bufferService).append(eq(1L), eq(12L), captor.capture());
        assertThat(captor.getValue()).containsExactly("line one", "password=[REDACTED]", "line three");
        assertThat(response.acceptedLines()).isEqualTo(3);
    }

    @Test
    void missingContentTypeDefaultsToPlainText() {
        allowRateLimit();
        service.ingest(principal, 12L, null, "hello");

        ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
        verify(bufferService).append(eq(1L), eq(12L), captor.capture());
        assertThat(captor.getValue()).containsExactly("hello");
    }

    @Test
    void ndjsonExtractsLogFieldWithMessageFallbackAndSkipsMalformedOrUnknownLines() {
        String body = """
                {"log":"first"}
                {"message":"second"}
                {"other":"ignored"}
                not-json

                {"log":""}
                """;

        allowRateLimit();
        LogIngestionAcceptedResponse response =
                service.ingest(principal, 12L, "application/x-ndjson; charset=UTF-8", body);

        ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
        verify(bufferService).append(eq(1L), eq(12L), captor.capture());
        assertThat(captor.getValue()).containsExactly("first", "second");
        assertThat(response.acceptedLines()).isEqualTo(2);
    }

    @Test
    void rejectsUnsupportedContentType() {
        allowRateLimit();
        assertThatThrownBy(() -> service.ingest(principal, 12L, "application/xml", "<a/>"))
                .isInstanceOfSatisfying(LogIngestionException.class,
                        e -> assertThat(e.getCode()).isEqualTo("LOG_INGESTION_UNSUPPORTED_CONTENT_TYPE"));
        verify(bufferService, never()).append(any(), any(), any());
    }

    @Test
    void rejectsWhenApiKeyMonitorDoesNotMatchRequestedMonitor() {
        assertThatThrownBy(() -> service.ingest(principal, 999L, "text/plain", "line"))
                .isInstanceOfSatisfying(LogIngestionException.class,
                        e -> assertThat(e.getCode()).isEqualTo("LOG_INGESTION_MONITOR_MISMATCH"));
        verify(bufferService, never()).append(any(), any(), any());
    }

    @Test
    void rejectsOversizedPayload() {
        properties.setMaxRequestBytes(10);
        String oversized = "a".repeat(11);

        assertThatThrownBy(() -> service.ingest(principal, 12L, "text/plain", oversized))
                .isInstanceOfSatisfying(LogIngestionException.class,
                        e -> assertThat(e.getCode()).isEqualTo("LOG_INGESTION_PAYLOAD_TOO_LARGE"));
        verify(bufferService, never()).append(any(), any(), any());
    }

    @Test
    void rejectsWhenRateLimitExceeded() {
        when(bufferService.tryConsumeRateLimit(1L, 12L)).thenReturn(false);

        assertThatThrownBy(() -> service.ingest(principal, 12L, "text/plain", "line"))
                .isInstanceOfSatisfying(LogIngestionException.class,
                        e -> assertThat(e.getCode()).isEqualTo("LOG_INGESTION_RATE_LIMITED"));
        verify(bufferService, never()).append(any(), any(), any());
    }

    @Test
    void blankBodyAcceptsZeroLines() {
        allowRateLimit();
        LogIngestionAcceptedResponse response = service.ingest(principal, 12L, "text/plain", "   ");

        assertThat(response.acceptedLines()).isZero();
        verify(bufferService).append(1L, 12L, List.of());
    }
}
