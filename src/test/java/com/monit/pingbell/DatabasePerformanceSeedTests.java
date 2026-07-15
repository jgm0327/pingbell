package com.monit.pingbell;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("db-seed")
@ActiveProfiles("migration-test")
@SpringBootTest(classes = DatabasePerformanceSeedTests.SeedTestApplication.class)
class DatabasePerformanceSeedTests {

    private static final Logger log = LoggerFactory.getLogger(DatabasePerformanceSeedTests.class);

    private static final String SEED_EMAIL_PATTERN = "seed-perf-%@pingbell.local";

    private final JdbcTemplate jdbcTemplate;
    private final int memberCount;
    private final int monitorCount;
    private final int checkResultCount;
    private final int incidentCount;
    private final int notificationHistoryCount;
    private final int hotMonitorCount;
    private final int hotCheckResultPercent;
    private final int dueMonitorPercent;
    private final int retryDueNotificationPercent;

    @Autowired
    DatabasePerformanceSeedTests(
            JdbcTemplate jdbcTemplate,
            @Value("${pingbell.seed.member-count:100}") int memberCount,
            @Value("${pingbell.seed.monitor-count:10000}") int monitorCount,
            @Value("${pingbell.seed.check-result-count:100000}") int checkResultCount,
            @Value("${pingbell.seed.incident-count:10000}") int incidentCount,
            @Value("${pingbell.seed.notification-history-count:100000}") int notificationHistoryCount,
            @Value("${pingbell.seed.hot-monitor-count:20}") int hotMonitorCount,
            @Value("${pingbell.seed.hot-check-result-percent:70}") int hotCheckResultPercent,
            @Value("${pingbell.seed.due-monitor-percent:3}") int dueMonitorPercent,
            @Value("${pingbell.seed.retry-due-notification-percent:4}") int retryDueNotificationPercent
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.memberCount = memberCount;
        this.monitorCount = monitorCount;
        this.checkResultCount = checkResultCount;
        this.incidentCount = incidentCount;
        this.notificationHistoryCount = notificationHistoryCount;
        this.hotMonitorCount = hotMonitorCount;
        this.hotCheckResultPercent = hotCheckResultPercent;
        this.dueMonitorPercent = dueMonitorPercent;
        this.retryDueNotificationPercent = retryDueNotificationPercent;
    }

    @Test
    void seedPerformanceData() {
        assertThat(memberCount).isPositive();
        assertThat(monitorCount).isPositive();
        assertThat(checkResultCount).isPositive();
        assertThat(incidentCount).isPositive();
        assertThat(notificationHistoryCount).isPositive();
        assertThat(hotMonitorCount).isBetween(1, monitorCount);
        assertThat(hotCheckResultPercent).isBetween(1, 99);
        assertThat(dueMonitorPercent).isBetween(1, 5);
        assertThat(retryDueNotificationPercent).isBetween(1, 5);

        deletePreviousSeedData();

        insertMembers();
        insertMonitors();
        insertCheckResults();
        insertIncidents();
        insertNotificationChannels();
        insertNotificationHistories();

        Map<String, Integer> counts = Map.of(
                "members", countSeedMembers(),
                "monitors", countSeedMonitors(),
                "checkResults", countSeedCheckResults(),
                "incidents", countSeedIncidents(),
                "notificationHistories", countSeedNotificationHistories()
        );
        Map<String, Integer> dueDistribution = dueDistribution();

        log.info("Seeded DB performance data: {}", counts);
        log.info("Seeded check result distribution: {}", checkResultDistribution());
        log.info("Seeded due distribution: {}", dueDistribution);

        assertThat(counts.get("members")).isEqualTo(memberCount);
        assertThat(counts.get("monitors")).isEqualTo(monitorCount);
        assertThat(counts.get("checkResults")).isEqualTo(checkResultCount);
        assertThat(counts.get("incidents")).isEqualTo(incidentCount);
        assertThat(counts.get("notificationHistories")).isEqualTo(notificationHistoryCount);
    }

    private void deletePreviousSeedData() {
        jdbcTemplate.update("""
                delete from notification_histories history
                using notification_channels channel, member seed_member
                where history.channel_id = channel.id
                  and channel.member_id = seed_member.id
                  and seed_member.email like ?
                """, SEED_EMAIL_PATTERN);
        jdbcTemplate.update("""
                delete from incidents incident
                using monitors monitor, member seed_member
                where incident.monitor_id = monitor.id
                  and monitor.user_id = seed_member.id
                  and seed_member.email like ?
                """, SEED_EMAIL_PATTERN);
        jdbcTemplate.update("""
                delete from check_results check_result
                using monitors monitor, member seed_member
                where check_result.monitor_id = monitor.id
                  and monitor.user_id = seed_member.id
                  and seed_member.email like ?
                """, SEED_EMAIL_PATTERN);
        jdbcTemplate.update("""
                delete from notification_channels channel
                using member seed_member
                where channel.member_id = seed_member.id
                  and seed_member.email like ?
                """, SEED_EMAIL_PATTERN);
        jdbcTemplate.update("""
                delete from monitors monitor
                using member seed_member
                where monitor.user_id = seed_member.id
                  and seed_member.email like ?
                """, SEED_EMAIL_PATTERN);
        jdbcTemplate.update("delete from member where email like ?", SEED_EMAIL_PATTERN);
    }

    private void insertMembers() {
        jdbcTemplate.update("""
                insert into member(email, password, created_at, updated_at)
                select 'seed-perf-' || gs || '@pingbell.local',
                       '{noop}seed-password',
                       now(),
                       now()
                from generate_series(1, ?) gs
                """, memberCount);
    }

    private void insertMonitors() {
        jdbcTemplate.update("""
                with seed_members as (
                    select id, row_number() over (order by id) as rn
                    from member
                    where email like ?
                )
                insert into monitors(
                    user_id,
                    name,
                    url,
                    interval_seconds,
                    timeout_millis,
                    failure_threshold,
                    recovery_threshold,
                    recovery_count,
                    failure_count,
                    status,
                    next_check_at,
                    deleted_at,
                    created_at,
                    updated_at
                )
                select seed_members.id,
                       'seed-monitor-' || gs,
                       'https://seed-' || gs || '.pingbell.local/health',
                       60 + (gs % 10) * 30,
                       3000,
                       3,
                       2,
                       gs % 2,
                       gs % 4,
                        case
                            when gs % 20 = 0 then 'PAUSED'
                            when gs % 7 = 0 then 'DOWN'
                            else 'ACTIVE'
                        end,
                        case
                            when gs % 20 = 0 then now() + ((gs % 7200) * interval '1 second')
                            when gs % 7 = 0 then
                                case
                                    when gs % 100 < ? then now() - ((gs % 3600) * interval '1 second')
                                    else now() + ((gs % 7200) * interval '1 second')
                                end
                            else
                                case
                                    when gs % 100 < ? then now() - ((gs % 3600) * interval '1 second')
                                    else now() + ((gs % 7200) * interval '1 second')
                                end
                        end,
                        case when gs % 100 = 0 then now() else null end,
                        now() - ((gs % 30) * interval '1 day'),
                        now()
                from generate_series(1, ?) gs
                join seed_members on seed_members.rn = ((gs - 1) % ?) + 1
                """, SEED_EMAIL_PATTERN, dueMonitorPercent, dueMonitorPercent, monitorCount, memberCount);
    }

    private void insertCheckResults() {
        jdbcTemplate.update("""
                with seed_monitors as (
                    select monitor.id, row_number() over (order by monitor.id) as rn
                    from monitors monitor
                    join member seed_member on seed_member.id = monitor.user_id
                    where seed_member.email like ?
                ),
                seed_source as (
                    select gs,
                           case
                               when gs <= (? * ? / 100) then
                                   1 + mod(abs(hashint8(gs::bigint * 1103515245)), ?)
                               else
                                   1 + mod(abs(hashint8(gs::bigint * 214013)), ?)
                           end as monitor_rn
                    from generate_series(1, ?) gs
                )
                insert into check_results(
                    monitor_id,
                    status,
                    http_status,
                    response_time_ms,
                    error_message,
                    created_at,
                    updated_at
                )
                select seed_monitors.id,
                       case
                           when seed_source.gs % 20 = 0 then 'TIMEOUT'
                           when seed_source.gs % 12 = 0 then 'HTTP_ERROR'
                           when seed_source.gs % 9 = 0 then 'SLOW_RESPONSE'
                           when seed_source.gs % 7 = 0 then 'FAILURE'
                           else 'SUCCESS'
                       end,
                       case
                           when seed_source.gs % 12 = 0 then 500
                           when seed_source.gs % 7 = 0 then null
                           else 200
                       end,
                       50 + (seed_source.gs % 2500),
                       case
                           when seed_source.gs % 20 = 0 then 'Connection timeout'
                           when seed_source.gs % 12 = 0 then 'HTTP 500'
                           when seed_source.gs % 7 = 0 then 'Connection failed'
                           else null
                       end,
                       now() - ((seed_source.gs % 259200) * interval '1 second'),
                       now()
                from seed_source
                join seed_monitors on seed_monitors.rn = seed_source.monitor_rn
                """,
                SEED_EMAIL_PATTERN,
                checkResultCount,
                hotCheckResultPercent,
                hotMonitorCount,
                monitorCount,
                checkResultCount
        );
    }

    private void insertIncidents() {
        jdbcTemplate.update("""
                with seed_monitors as (
                    select monitor.id, row_number() over (order by monitor.id) as rn
                    from monitors monitor
                    join member seed_member on seed_member.id = monitor.user_id
                    where seed_member.email like ?
                )
                insert into incidents(
                    monitor_id,
                    status,
                    started_at,
                    resolved_at,
                    created_at,
                    updated_at,
                    last_error_message
                )
                select seed_monitors.id,
                       case when gs <= ? and gs % 5 = 0 then 'OPEN' else 'RESOLVED' end,
                       now() - ((gs % 604800) * interval '1 second'),
                       case when gs <= ? and gs % 5 = 0 then null else now() - ((gs % 3600) * interval '1 second') end,
                       now() - ((gs % 604800) * interval '1 second'),
                       now(),
                       'Seed incident error ' || gs
                from generate_series(1, ?) gs
                join seed_monitors on seed_monitors.rn = ((gs - 1) % ?) + 1
                """, SEED_EMAIL_PATTERN, monitorCount, monitorCount, incidentCount, monitorCount);
    }

    private void insertNotificationChannels() {
        jdbcTemplate.update("""
                with seed_members as (
                    select id, row_number() over (order by id) as rn
                    from member
                    where email like ?
                )
                insert into notification_channels(
                    public_id,
                    member_id,
                    type,
                    target,
                    enabled,
                    created_at,
                    updated_at
                )
                select ('00000000-0000-0000-0000-' || lpad(rn::text, 12, '0'))::uuid,
                       id,
                       case when rn % 3 = 0 then 'SLACK' when rn % 3 = 1 then 'EMAIL' else 'DISCORD' end,
                       'seed-target-' || rn || '@pingbell.local',
                       rn % 10 <> 0,
                       now(),
                       now()
                from seed_members
                """, SEED_EMAIL_PATTERN);
    }

    private void insertNotificationHistories() {
        jdbcTemplate.update("""
                with seed_incidents as (
                    select incident.id, row_number() over (order by incident.id) as rn
                    from incidents incident
                    join monitors monitor on monitor.id = incident.monitor_id
                    join member seed_member on seed_member.id = monitor.user_id
                    where seed_member.email like ?
                ),
                seed_channels as (
                    select channel.id, row_number() over (order by channel.id) as rn
                    from notification_channels channel
                    join member seed_member on seed_member.id = channel.member_id
                    where seed_member.email like ?
                )
                insert into notification_histories(
                    incident_id,
                    channel_id,
                    notification_type,
                    status,
                    retry_count,
                    max_retry_count,
                    next_retry_at,
                    last_attempted_at,
                    retryable,
                    error_message,
                    sent_at,
                    manual_resend,
                    resend_of_history_id,
                    created_at,
                    updated_at
                )
                select seed_incidents.id,
                       seed_channels.id,
                        case when gs % 2 = 0 then 'INCIDENT_OPEN' else 'INCIDENT_RESOLVED' end,
                        case
                            when gs % 10 in (0, 1) then 'FAILED'
                            when gs % 100 < ? then 'RETRY_PENDING'
                            when gs % 10 in (3, 4, 5, 6) then 'SENT'
                            else 'PENDING'
                        end,
                        case
                            when gs % 100 < ? then 1
                            when gs % 15 = 0 then 2
                            else 0
                        end,
                        2,
                        case when gs % 100 < ? then now() - ((gs % 3600) * interval '1 second') else null end,
                        case
                            when gs % 100 < ? then now() - ((gs % 3600) * interval '1 second')
                            when gs % 10 in (0, 1, 3, 4, 5, 6) then now() - ((gs % 3600) * interval '1 second')
                            else null
                        end,
                        gs % 100 < ?,
                        case
                            when gs % 25 = 0 then 'Notification channel is disabled.'
                            when gs % 100 < ? then 'Seed send failure'
                            else null
                        end,
                       case when gs % 10 in (3, 4, 5, 6) then now() - ((gs % 3600) * interval '1 second') else null end,
                       false,
                       null,
                       now() - ((gs % 604800) * interval '1 second'),
                       now()
                from generate_series(1, ?) gs
                join seed_incidents on seed_incidents.rn = ((gs - 1) % ?) + 1
                join seed_channels on seed_channels.rn = ((gs - 1) % ?) + 1
                """,
                SEED_EMAIL_PATTERN,
                SEED_EMAIL_PATTERN,
                retryDueNotificationPercent,
                retryDueNotificationPercent,
                retryDueNotificationPercent,
                retryDueNotificationPercent,
                retryDueNotificationPercent,
                retryDueNotificationPercent,
                notificationHistoryCount,
                incidentCount,
                memberCount
        );
    }

    private int countSeedMembers() {
        return jdbcTemplate.queryForObject(
                "select count(*) from member where email like ?",
                Integer.class,
                SEED_EMAIL_PATTERN
        );
    }

    private int countSeedMonitors() {
        return jdbcTemplate.queryForObject("""
                select count(*)
                from monitors monitor
                join member seed_member on seed_member.id = monitor.user_id
                where seed_member.email like ?
                """, Integer.class, SEED_EMAIL_PATTERN);
    }

    private int countSeedCheckResults() {
        return jdbcTemplate.queryForObject("""
                select count(*)
                from check_results check_result
                join monitors monitor on monitor.id = check_result.monitor_id
                join member seed_member on seed_member.id = monitor.user_id
                where seed_member.email like ?
                """, Integer.class, SEED_EMAIL_PATTERN);
    }

    private int countSeedIncidents() {
        return jdbcTemplate.queryForObject("""
                select count(*)
                from incidents incident
                join monitors monitor on monitor.id = incident.monitor_id
                join member seed_member on seed_member.id = monitor.user_id
                where seed_member.email like ?
                """, Integer.class, SEED_EMAIL_PATTERN);
    }

    private int countSeedNotificationHistories() {
        return jdbcTemplate.queryForObject("""
                select count(*)
                from notification_histories history
                join notification_channels channel on channel.id = history.channel_id
                join member seed_member on seed_member.id = channel.member_id
                where seed_member.email like ?
                """, Integer.class, SEED_EMAIL_PATTERN);
    }

    private Map<String, Integer> checkResultDistribution() {
        return jdbcTemplate.queryForObject("""
                with per_monitor as (
                    select check_result.monitor_id, count(*)::int as check_count
                    from check_results check_result
                    join monitors monitor on monitor.id = check_result.monitor_id
                    join member seed_member on seed_member.id = monitor.user_id
                    where seed_member.email like ?
                    group by check_result.monitor_id
                )
                select count(*)::int as monitors_with_checks,
                       min(check_count)::int as min_per_monitor,
                       max(check_count)::int as max_per_monitor,
                       avg(check_count)::int as avg_per_monitor
                from per_monitor
                """,
                (rs, rowNum) -> Map.of(
                        "monitorsWithChecks", rs.getInt("monitors_with_checks"),
                        "minPerMonitor", rs.getInt("min_per_monitor"),
                        "maxPerMonitor", rs.getInt("max_per_monitor"),
                        "avgPerMonitor", rs.getInt("avg_per_monitor")
                ),
                SEED_EMAIL_PATTERN
        );
    }

    private Map<String, Integer> dueDistribution() {
        return jdbcTemplate.queryForObject("""
                with due_monitor_stats as (
                    select count(*)::int as total_monitors,
                           count(*) filter (
                               where monitor.status in ('ACTIVE', 'DOWN')
                                 and monitor.deleted_at is null
                                 and monitor.next_check_at <= now()
                           )::int as due_monitors
                    from monitors monitor
                    join member seed_member on seed_member.id = monitor.user_id
                    where seed_member.email like ?
                ),
                retry_due_stats as (
                    select count(*)::int as total_histories,
                           count(*) filter (
                               where history.retryable = true
                                 and history.next_retry_at is not null
                                 and history.next_retry_at <= now()
                                 and history.retry_count < history.max_retry_count
                           )::int as retry_due_histories
                    from notification_histories history
                    join notification_channels channel on channel.id = history.channel_id
                    join member seed_member on seed_member.id = channel.member_id
                    where seed_member.email like ?
                )
                select due_monitor_stats.total_monitors,
                       due_monitor_stats.due_monitors,
                       retry_due_stats.total_histories,
                       retry_due_stats.retry_due_histories
                from due_monitor_stats
                cross join retry_due_stats
                """,
                (rs, rowNum) -> Map.of(
                        "totalMonitors", rs.getInt("total_monitors"),
                        "dueMonitors", rs.getInt("due_monitors"),
                        "totalHistories", rs.getInt("total_histories"),
                        "retryDueHistories", rs.getInt("retry_due_histories")
                ),
                SEED_EMAIL_PATTERN,
                SEED_EMAIL_PATTERN
        );
    }

    @Configuration
    @EnableAutoConfiguration
    static class SeedTestApplication {
    }
}
