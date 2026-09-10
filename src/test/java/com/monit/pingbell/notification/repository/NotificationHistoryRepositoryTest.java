package com.monit.pingbell.notification.repository;

import com.monit.pingbell.incident.domain.Incident;
import com.monit.pingbell.incident.domain.IncidentStatus;
import com.monit.pingbell.member.domain.Member;
import com.monit.pingbell.monitor.domain.Monitor;
import com.monit.pingbell.monitor.domain.MonitorStatus;
import com.monit.pingbell.notification.domain.NotificationChannel;
import com.monit.pingbell.notification.domain.NotificationHistory;
import com.monit.pingbell.notification.type.NotificationChannelType;
import com.monit.pingbell.notification.type.NotificationStatus;
import com.monit.pingbell.notification.type.NotificationType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class NotificationHistoryRepositoryTest {

    @Autowired
    private NotificationHistoryRepository historyRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void findRetryExhaustedFailuresByChannelMemberIdReturnsOnlyFailedHistoriesWithExhaustedRetryCount() {
        Member member = persistMember("user@example.com");
        Incident incident = persistIncident(member);
        NotificationChannel channel = persistChannel(member);
        NotificationHistory retryExhaustedFailure = persistHistory(incident, channel);
        retryExhaustedFailure.increaseRetryCount();
        retryExhaustedFailure.increaseRetryCount();
        retryExhaustedFailure.markFailed("retry exhausted", LocalDateTime.of(2026, 7, 2, 10, 0));

        NotificationHistory immediateFailure = persistHistory(incident, channel);
        immediateFailure.markFailed("immediate failure", LocalDateTime.of(2026, 7, 2, 10, 1));

        NotificationHistory channelDisabledFailure = persistHistory(incident, channel);
        channelDisabledFailure.increaseRetryCount();
        channelDisabledFailure.increaseRetryCount();
        channelDisabledFailure.markFailed(
                NotificationHistory.CHANNEL_DISABLED_ERROR_MESSAGE,
                LocalDateTime.of(2026, 7, 2, 10, 2)
        );

        Member otherMember = persistMember("other@example.com");
        Incident otherIncident = persistIncident(otherMember);
        NotificationChannel otherChannel = persistChannel(otherMember);
        NotificationHistory otherMemberRetryExhaustedFailure = persistHistory(otherIncident, otherChannel);
        otherMemberRetryExhaustedFailure.increaseRetryCount();
        otherMemberRetryExhaustedFailure.increaseRetryCount();
        otherMemberRetryExhaustedFailure.markFailed("other retry exhausted", LocalDateTime.of(2026, 7, 2, 10, 3));

        entityManager.flush();
        entityManager.clear();

        var histories = historyRepository.findRetryExhaustedFailuresByChannelMemberId(
                member.getId(),
                PageRequest.of(0, 20)
        );

        assertThat(histories.getContent())
                .extracting(NotificationHistory::getId)
                .containsExactly(retryExhaustedFailure.getId());
    }

    @Test
    void findChannelDisabledFailuresByChannelMemberIdReturnsOnlyDisabledChannelFailures() {
        Member member = persistMember("user@example.com");
        Incident incident = persistIncident(member);
        NotificationChannel channel = persistChannel(member);
        NotificationHistory channelDisabledFailure = persistHistory(incident, channel);
        channelDisabledFailure.markFailed(
                NotificationHistory.CHANNEL_DISABLED_ERROR_MESSAGE,
                LocalDateTime.of(2026, 7, 2, 10, 0)
        );

        NotificationHistory sendFailure = persistHistory(incident, channel);
        sendFailure.markFailed("404 Not Found", LocalDateTime.of(2026, 7, 2, 10, 1));

        entityManager.flush();
        entityManager.clear();

        var histories = historyRepository.findChannelDisabledFailuresByChannelMemberId(
                member.getId(),
                PageRequest.of(0, 20)
        );

        assertThat(histories.getContent())
                .extracting(NotificationHistory::getId)
                .containsExactly(channelDisabledFailure.getId());
    }

    @Test
    void findSendFailedFailuresByChannelMemberIdReturnsOnlyFailedHistoriesBeforeRetryExhaustion() {
        Member member = persistMember("user@example.com");
        Incident incident = persistIncident(member);
        NotificationChannel channel = persistChannel(member);
        NotificationHistory sendFailure = persistHistory(incident, channel);
        sendFailure.markFailed("404 Not Found", LocalDateTime.of(2026, 7, 2, 10, 0));

        NotificationHistory retryExhaustedFailure = persistHistory(incident, channel);
        retryExhaustedFailure.increaseRetryCount();
        retryExhaustedFailure.increaseRetryCount();
        retryExhaustedFailure.markFailed("retry exhausted", LocalDateTime.of(2026, 7, 2, 10, 1));

        NotificationHistory channelDisabledFailure = persistHistory(incident, channel);
        channelDisabledFailure.markFailed(
                NotificationHistory.CHANNEL_DISABLED_ERROR_MESSAGE,
                LocalDateTime.of(2026, 7, 2, 10, 2)
        );

        entityManager.flush();
        entityManager.clear();

        var histories = historyRepository.findSendFailedFailuresByChannelMemberId(
                member.getId(),
                PageRequest.of(0, 20)
        );

        assertThat(histories.getContent())
                .extracting(NotificationHistory::getId)
                .containsExactly(sendFailure.getId());
    }

    @Test
    void countByStatusCountsRetryPendingHistoriesAcrossAllMembersForTheObservabilityGauge() {
        Member member = persistMember("user@example.com");
        Incident incident = persistIncident(member);
        NotificationChannel channel = persistChannel(member);
        NotificationHistory retryPending = persistHistory(incident, channel);
        retryPending.markRetryPending("connection refused", LocalDateTime.of(2026, 7, 2, 10, 0), LocalDateTime.of(2026, 7, 2, 10, 1));

        NotificationHistory sent = persistHistory(incident, channel);
        sent.markSent(LocalDateTime.of(2026, 7, 2, 10, 0));

        Member otherMember = persistMember("other@example.com");
        Incident otherIncident = persistIncident(otherMember);
        NotificationChannel otherChannel = persistChannel(otherMember);
        NotificationHistory otherMemberRetryPending = persistHistory(otherIncident, otherChannel);
        otherMemberRetryPending.markRetryPending("timeout", LocalDateTime.of(2026, 7, 2, 10, 2), LocalDateTime.of(2026, 7, 2, 10, 3));

        entityManager.flush();
        entityManager.clear();

        // 이 gauge는 운영 전체 상태를 보려는 목적이라 member로 스코프하지 않는다 - 두 member의 RETRY_PENDING이 합산되어야 한다.
        assertThat(historyRepository.countByStatus(NotificationStatus.RETRY_PENDING)).isEqualTo(2L);
    }

    private Member persistMember(String email) {
        Member member = Member.builder()
                .email(email)
                .password("password")
                .build();
        entityManager.persist(member);
        return member;
    }

    private Incident persistIncident(Member member) {
        Monitor monitor = Monitor.builder()
                .member(member)
                .name("API server")
                .url("https://api.example.com/health")
                .intervalSeconds(60)
                .timeoutMillis(1000)
                .failureThreshold(3)
                .recoveryThreshold(2)
                .status(MonitorStatus.ACTIVE)
                .nextCheckAt(LocalDateTime.of(2026, 7, 2, 9, 0))
                .build();
        entityManager.persist(monitor);

        Incident incident = Incident.builder()
                .monitor(monitor)
                .status(IncidentStatus.OPEN)
                .startedAt(LocalDateTime.of(2026, 7, 2, 9, 30))
                .lastErrorMessage("timeout")
                .build();
        entityManager.persist(incident);
        return incident;
    }

    private NotificationChannel persistChannel(Member member) {
        NotificationChannel channel = new NotificationChannel(
                member,
                NotificationChannelType.SLACK,
                " "
        );
        entityManager.persist(channel);
        return channel;
    }

    private NotificationHistory persistHistory(Incident incident, NotificationChannel channel) {
        NotificationHistory history = new NotificationHistory(
                incident,
                channel,
                NotificationType.INCIDENT_OPEN
        );
        entityManager.persist(history);
        return history;
    }
}
