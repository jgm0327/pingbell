package com.monit.pingbell.logingestion.service;

import com.monit.pingbell.logingestion.config.LogIngestionProperties;
import com.monit.pingbell.logingestion.domain.LogIngestionApiKey;
import com.monit.pingbell.logingestion.dto.LogIngestionApiKeyIssueResponse;
import com.monit.pingbell.logingestion.exception.LogIngestionException;
import com.monit.pingbell.logingestion.repository.LogIngestionApiKeyRepository;
import com.monit.pingbell.logingestion.security.AuthenticatedIngestionKey;
import com.monit.pingbell.monitor.domain.Monitor;
import com.monit.pingbell.monitor.repository.MonitorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LogIngestionApiKeyServiceTest {

    @Mock
    private LogIngestionApiKeyRepository apiKeyRepository;

    @Mock
    private MonitorRepository monitorRepository;

    private final Clock clock = Clock.fixed(Instant.parse("2026-08-25T00:00:00Z"), ZoneOffset.UTC);
    private LogIngestionApiKeyService service;

    @BeforeEach
    void setUp() {
        var configTemplateService = new LogIngestionConfigTemplateService(new LogIngestionProperties());
        service = new LogIngestionApiKeyService(apiKeyRepository, monitorRepository, configTemplateService, clock);
    }

    @Test
    void issueGeneratesPrefixedKeyAndStoresOnlyItsHash() {
        when(monitorRepository.findByIdAndMemberIdAndDeletedAtIsNull(12L, 1L))
                .thenReturn(Optional.of(mock(Monitor.class)));
        ArgumentCaptor<LogIngestionApiKey> captor = ArgumentCaptor.forClass(LogIngestionApiKey.class);
        when(apiKeyRepository.save(captor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        LogIngestionApiKeyIssueResponse response = service.issue(1L, 12L);

        assertThat(response.apiKey()).startsWith("pgbl_");
        assertThat(response.keyPrefix()).isEqualTo(response.apiKey().substring(0, 12));

        LogIngestionApiKey saved = captor.getValue();
        assertThat(saved.getMonitorId()).isEqualTo(12L);
        assertThat(saved.getTenantId()).isEqualTo(1L);
        assertThat(saved.getKeyHash()).hasSize(64).isNotEqualTo(response.apiKey());

        assertThat(response.fluentBitConfig())
                .contains("URI          /api/v1/monitors/12/logs")
                .contains("Header       Authorization Bearer " + response.apiKey());
        assertThat(response.dockerComposeSnippet()).contains("fluent-bit-monitor-12");
    }

    @Test
    void issueProducesDifferentKeysOnEachCall() {
        when(monitorRepository.findByIdAndMemberIdAndDeletedAtIsNull(12L, 1L))
                .thenReturn(Optional.of(mock(Monitor.class)));
        when(apiKeyRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        String first = service.issue(1L, 12L).apiKey();
        String second = service.issue(1L, 12L).apiKey();

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void issueRejectsForeignOrDeletedMonitor() {
        when(monitorRepository.findByIdAndMemberIdAndDeletedAtIsNull(12L, 2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.issue(2L, 12L))
                .isInstanceOfSatisfying(LogIngestionException.class,
                        e -> assertThat(e.getCode()).isEqualTo("MONITOR_NOT_FOUND"));
        verify(apiKeyRepository, never()).save(any());
    }

    @Test
    void authenticateReturnsEmptyForUnknownKeyWithoutThrowing() {
        when(apiKeyRepository.findByKeyHash(anyString())).thenReturn(Optional.empty());

        assertThat(service.authenticate("pgbl_unknown")).isEmpty();
    }

    @Test
    void authenticateRejectsRevokedKeyAndDoesNotRecordUse() {
        LogIngestionApiKey key = new LogIngestionApiKey(12L, 1L, "hash", "pgbl_abc");
        key.revoke(LocalDateTime.now(clock));
        when(apiKeyRepository.findByKeyHash(anyString())).thenReturn(Optional.of(key));

        assertThat(service.authenticate("pgbl_abc")).isEmpty();
        assertThat(key.getLastUsedAt()).isNull();
    }

    @Test
    void authenticateAcceptsActiveKeyAndRecordsUse() {
        LogIngestionApiKey key = new LogIngestionApiKey(12L, 1L, "hash", "pgbl_abc");
        when(apiKeyRepository.findByKeyHash(anyString())).thenReturn(Optional.of(key));

        Optional<AuthenticatedIngestionKey> authenticated = service.authenticate("pgbl_abc");

        assertThat(authenticated).contains(new AuthenticatedIngestionKey(12L, 1L));
        assertThat(key.getLastUsedAt()).isEqualTo(LocalDateTime.now(clock));
    }

    @Test
    void revokeRequiresOwnedMonitorAndExistingKey() {
        when(monitorRepository.findByIdAndMemberIdAndDeletedAtIsNull(12L, 1L))
                .thenReturn(Optional.of(mock(Monitor.class)));
        when(apiKeyRepository.findByIdAndTenantIdAndMonitorId(5L, 1L, 12L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.revoke(1L, 12L, 5L))
                .isInstanceOfSatisfying(LogIngestionException.class,
                        e -> assertThat(e.getCode()).isEqualTo("LOG_INGESTION_API_KEY_NOT_FOUND"));
    }

    @Test
    void revokeMarksKeyRevokedAtCurrentTime() {
        when(monitorRepository.findByIdAndMemberIdAndDeletedAtIsNull(12L, 1L))
                .thenReturn(Optional.of(mock(Monitor.class)));
        LogIngestionApiKey key = new LogIngestionApiKey(12L, 1L, "hash", "pgbl_abc");
        when(apiKeyRepository.findByIdAndTenantIdAndMonitorId(5L, 1L, 12L)).thenReturn(Optional.of(key));

        service.revoke(1L, 12L, 5L);

        assertThat(key.isRevoked()).isTrue();
        assertThat(key.getRevokedAt()).isEqualTo(LocalDateTime.now(clock));
    }
}
