package com.monit.pingbell.incident.repository;

import com.monit.pingbell.incident.domain.Incident;
import com.monit.pingbell.incident.domain.IncidentStatus;
import com.monit.pingbell.member.domain.Member;
import com.monit.pingbell.monitor.domain.Monitor;
import com.monit.pingbell.monitor.domain.MonitorStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class IncidentRepositoryTest {

    @Autowired
    private IncidentRepository incidentRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void countByStatusCountsOpenIncidentsAcrossAllMembersForTheObservabilityGauge() {
        Member member = persistMember("user@example.com");
        persistIncident(member, IncidentStatus.OPEN);
        persistIncident(member, IncidentStatus.RESOLVED);

        Member otherMember = persistMember("other@example.com");
        persistIncident(otherMember, IncidentStatus.OPEN);

        entityManager.flush();
        entityManager.clear();

        // 이 gauge는 운영 전체 상태를 보려는 목적이라 member로 스코프하지 않는다 - 두 member의 OPEN이 합산되어야 한다.
        assertThat(incidentRepository.countByStatus(IncidentStatus.OPEN)).isEqualTo(2L);
        assertThat(incidentRepository.countByStatus(IncidentStatus.RESOLVED)).isEqualTo(1L);
    }

    private Member persistMember(String email) {
        Member member = Member.builder()
                .email(email)
                .password("password")
                .build();
        entityManager.persist(member);
        return member;
    }

    private void persistIncident(Member member, IncidentStatus status) {
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
                .status(status)
                .startedAt(LocalDateTime.of(2026, 7, 2, 9, 30))
                .lastErrorMessage("timeout")
                .build();
        entityManager.persist(incident);
    }
}
