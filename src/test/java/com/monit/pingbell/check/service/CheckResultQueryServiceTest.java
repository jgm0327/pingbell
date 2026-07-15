package com.monit.pingbell.check.service;

import com.monit.pingbell.check.domain.CheckStatus;
import com.monit.pingbell.check.dto.MonitorCheckTrendRawPoint;
import com.monit.pingbell.check.dto.MonitorFailureSummaryRawPoint;
import com.monit.pingbell.check.repository.CheckResultRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CheckResultQueryServiceTest {

    @Mock
    private CheckResultRepository checkResultRepository;

    @InjectMocks
    private CheckResultQueryService checkResultQueryService;

    @Test
    void getMonitorCheckSummaryFillsHourlyBucketsAndAggregatesFailureStatuses() {
        Long monitorId = 10L;
        LocalDateTime now = LocalDateTime.of(2026, 6, 29, 12, 30);
        LocalDateTime firstBucketStart = LocalDateTime.of(2026, 6, 28, 13, 0);
        LocalDateTime since = now.minusHours(24);

        when(checkResultRepository.findHourlyTrendByMonitorId(monitorId, firstBucketStart, now.plusNanos(1)))
                .thenReturn(List.of(
                        new MonitorCheckTrendRawPoint(2026, 6, 28, 13, 2L, 1L, 120.4),
                        new MonitorCheckTrendRawPoint(2026, 6, 29, 12, 1L, 2L, 300.6)
                ));
        when(checkResultRepository.countFailuresByStatusSince(monitorId, since))
                .thenReturn(List.of(
                        new MonitorFailureSummaryRawPoint(CheckStatus.TIMEOUT, 3L),
                        new MonitorFailureSummaryRawPoint(CheckStatus.HTTP_ERROR, 1L)
                ));

        var response = checkResultQueryService.getMonitorCheckSummary(monitorId, now);

        assertThat(response.windowHours()).isEqualTo(24);
        assertThat(response.since()).isEqualTo(since);
        assertThat(response.trend()).hasSize(24);
        assertThat(response.trend().getFirst().bucketStart()).isEqualTo(firstBucketStart);
        assertThat(response.trend().getFirst().successCount()).isEqualTo(2L);
        assertThat(response.trend().getFirst().failureCount()).isEqualTo(1L);
        assertThat(response.trend().getFirst().totalCount()).isEqualTo(3L);
        assertThat(response.trend().getFirst().averageResponseTimeMs()).isEqualTo(120L);
        assertThat(response.trend().get(1).totalCount()).isZero();
        assertThat(response.trend().getLast().bucketStart()).isEqualTo(LocalDateTime.of(2026, 6, 29, 12, 0));
        assertThat(response.trend().getLast().averageResponseTimeMs()).isEqualTo(301L);
        assertThat(response.failureSummary())
                .extracting("status", "count")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(CheckStatus.TIMEOUT, 3L),
                        org.assertj.core.groups.Tuple.tuple(CheckStatus.HTTP_ERROR, 1L)
                );
    }
}
