package com.monit.pingbell.check.service;

import com.monit.pingbell.check.dto.CheckResultResponse;
import com.monit.pingbell.check.dto.MonitorCheckSummaryResponse;
import com.monit.pingbell.check.dto.MonitorCheckTrendRawPoint;
import com.monit.pingbell.check.repository.CheckResultRepository;
import com.monit.pingbell.global.common.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
public class CheckResultQueryService {
    private static final int DEFAULT_WINDOW_HOURS = 24;
    private static final int DEFAULT_TREND_BUCKET_COUNT = 24;

    private final CheckResultRepository checkResultRepository;

    @Transactional(readOnly = true)
    public PageResponse<CheckResultResponse> getCheckResults(Long monitorId, Pageable pageable) {
        return PageResponse.from(
                checkResultRepository.findAllByMonitorIdOrderByIdDesc(monitorId, pageable)
                        .map(CheckResultResponse::from)
        );
    }

    @Transactional(readOnly = true)
    public CheckResultResponse getLatestCheckResult(Long monitorId) {
        return checkResultRepository.findFirstByMonitorIdOrderByCreatedAtDesc(monitorId)
                .map(CheckResultResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Check result not found."));
    }

    @Transactional(readOnly = true)
    public MonitorCheckSummaryResponse getMonitorCheckSummary(Long monitorId, LocalDateTime now) {
        LocalDateTime since = now.minusHours(DEFAULT_WINDOW_HOURS);

        return new MonitorCheckSummaryResponse(
                DEFAULT_WINDOW_HOURS,
                since,
                getHourlyTrend(monitorId, now),
                checkResultRepository.countFailuresByStatusSince(monitorId, since)
                        .stream()
                        .map(point -> new MonitorCheckSummaryResponse.FailureSummary(point.status(), point.count()))
                        .toList()
        );
    }

    private List<MonitorCheckSummaryResponse.TrendPoint> getHourlyTrend(Long monitorId, LocalDateTime now) {
        LocalDateTime firstBucketStart = now.truncatedTo(ChronoUnit.HOURS).minusHours(DEFAULT_TREND_BUCKET_COUNT - 1L);
        LocalDateTime until = now.plusNanos(1);
        Map<LocalDateTime, MonitorCheckTrendRawPoint> rawPoints = new HashMap<>();

        for (MonitorCheckTrendRawPoint rawPoint : checkResultRepository.findHourlyTrendByMonitorId(monitorId, firstBucketStart, until)) {
            rawPoints.put(
                    LocalDateTime.of(rawPoint.year(), rawPoint.month(), rawPoint.day(), rawPoint.hour(), 0),
                    rawPoint
            );
        }

        return IntStream.range(0, DEFAULT_TREND_BUCKET_COUNT)
                .mapToObj(firstBucketStart::plusHours)
                .map(bucketStart -> toTrendPoint(bucketStart, rawPoints.get(bucketStart)))
                .toList();
    }

    private MonitorCheckSummaryResponse.TrendPoint toTrendPoint(LocalDateTime bucketStart, MonitorCheckTrendRawPoint rawPoint) {
        if (rawPoint == null) {
            return new MonitorCheckSummaryResponse.TrendPoint(bucketStart, 0, 0, 0, 0);
        }

        long successCount = rawPoint.successCount() == null ? 0 : rawPoint.successCount();
        long failureCount = rawPoint.failureCount() == null ? 0 : rawPoint.failureCount();
        long averageResponseTimeMs = rawPoint.averageResponseTimeMs() == null
                ? 0
                : Math.round(rawPoint.averageResponseTimeMs());

        return new MonitorCheckSummaryResponse.TrendPoint(
                bucketStart,
                successCount,
                failureCount,
                successCount + failureCount,
                averageResponseTimeMs
        );
    }
}
