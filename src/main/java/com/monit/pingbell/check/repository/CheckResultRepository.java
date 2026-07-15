package com.monit.pingbell.check.repository;

import com.monit.pingbell.check.domain.CheckResult;
import com.monit.pingbell.check.domain.CheckStatus;
import com.monit.pingbell.check.dto.MonitorCheckTrendRawPoint;
import com.monit.pingbell.check.dto.MonitorFailureSummaryRawPoint;
import com.monit.pingbell.dashboard.dto.HealthCheckTrendRawPoint;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CheckResultRepository extends JpaRepository<CheckResult, Long> {
    List<CheckResult> findAllByMonitorIdAndMonitorMemberIdOrderByCreatedAtDesc(Long monitorId, Long memberId);

    Optional<CheckResult> findFirstByMonitorIdAndMonitorMemberIdOrderByCreatedAtDesc(Long monitorId, Long memberId);

    Page<CheckResult> findAllByMonitorIdOrderByIdDesc(Long monitorId, Pageable pageable);

    Optional<CheckResult> findFirstByMonitorIdOrderByCreatedAtDesc(Long monitorId);

    long countByMonitorMemberIdAndStatusAndCreatedAtGreaterThanEqual(Long memberId, CheckStatus status, LocalDateTime since);

    long countByMonitorMemberIdAndStatusInAndCreatedAtGreaterThanEqual(Long memberId, Collection<CheckStatus> statuses, LocalDateTime since);

    @Query("""
            select new com.monit.pingbell.dashboard.dto.HealthCheckTrendRawPoint(
                year(c.createdAt),
                month(c.createdAt),
                day(c.createdAt),
                hour(c.createdAt),
                sum(case when c.status = com.monit.pingbell.check.domain.CheckStatus.SUCCESS then 1L else 0L end),
                sum(case when c.status <> com.monit.pingbell.check.domain.CheckStatus.SUCCESS then 1L else 0L end),
                avg(c.responseTimeMs)
            )
            from CheckResult c
            where c.monitor.member.id = :memberId
              and c.createdAt >= :since
              and c.createdAt < :until
            group by year(c.createdAt), month(c.createdAt), day(c.createdAt), hour(c.createdAt)
            order by year(c.createdAt), month(c.createdAt), day(c.createdAt), hour(c.createdAt)
            """)
    List<HealthCheckTrendRawPoint> findHourlyTrendByMemberId(
            @Param("memberId") Long memberId,
            @Param("since") LocalDateTime since,
            @Param("until") LocalDateTime until
    );

    @Query("""
            select new com.monit.pingbell.check.dto.MonitorCheckTrendRawPoint(
                year(c.createdAt),
                month(c.createdAt),
                day(c.createdAt),
                hour(c.createdAt),
                sum(case when c.status = com.monit.pingbell.check.domain.CheckStatus.SUCCESS then 1L else 0L end),
                sum(case when c.status <> com.monit.pingbell.check.domain.CheckStatus.SUCCESS then 1L else 0L end),
                avg(c.responseTimeMs)
            )
            from CheckResult c
            where c.monitor.id = :monitorId
              and c.createdAt >= :since
              and c.createdAt < :until
            group by year(c.createdAt), month(c.createdAt), day(c.createdAt), hour(c.createdAt)
            order by year(c.createdAt), month(c.createdAt), day(c.createdAt), hour(c.createdAt)
            """)
    List<MonitorCheckTrendRawPoint> findHourlyTrendByMonitorId(
            @Param("monitorId") Long monitorId,
            @Param("since") LocalDateTime since,
            @Param("until") LocalDateTime until
    );

    @Query("""
            select new com.monit.pingbell.check.dto.MonitorFailureSummaryRawPoint(c.status, count(c))
            from CheckResult c
            where c.monitor.id = :monitorId
              and c.createdAt >= :since
              and c.status <> com.monit.pingbell.check.domain.CheckStatus.SUCCESS
            group by c.status
            order by count(c) desc
            """)
    List<MonitorFailureSummaryRawPoint> countFailuresByStatusSince(
            @Param("monitorId") Long monitorId,
            @Param("since") LocalDateTime since
    );
}
