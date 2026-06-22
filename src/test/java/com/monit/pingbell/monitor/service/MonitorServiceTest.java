package com.monit.pingbell.monitor.service;

import com.monit.pingbell.member.domain.Member;
import com.monit.pingbell.member.repository.MemberRepository;
import com.monit.pingbell.monitor.domain.Monitor;
import com.monit.pingbell.monitor.domain.MonitorStatus;
import com.monit.pingbell.monitor.dto.MonitorUpdateRequest;
import com.monit.pingbell.monitor.repository.MonitorRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MonitorServiceTest {

    @Mock
    private MonitorRepository monitorRepository;

    @Mock
    private MemberRepository memberRepository;

    @InjectMocks
    private MonitorService monitorService;

    @Test
    void updateMonitorChangesConfigAndRefreshesNextCheckAt() {
        Member member = member();
        Monitor monitor = monitor(member);
        LocalDateTime previousNextCheckAt = monitor.getNextCheckAt();

        when(monitorRepository.findByIdAndMemberIdAndDeletedAtIsNull(10L, 1L))
                .thenReturn(Optional.of(monitor));

        var response = monitorService.updateMonitor(1L, 10L, new MonitorUpdateRequest(
                "updated api",
                "https://example.com/health",
                120,
                5000,
                4,
                3
        ));

        assertThat(response.name()).isEqualTo("updated api");
        assertThat(response.url()).isEqualTo("https://example.com/health");
        assertThat(response.intervalSeconds()).isEqualTo(120);
        assertThat(response.timeoutMillis()).isEqualTo(5000);
        assertThat(response.failureThreshold()).isEqualTo(4);
        assertThat(response.recoveryThreshold()).isEqualTo(3);
        assertThat(response.nextCheckAt()).isAfter(previousNextCheckAt);
    }

    @Test
    void pauseMonitorChangesStatusToPaused() {
        Member member = member();
        Monitor monitor = monitor(member);

        when(monitorRepository.findByIdAndMemberIdAndDeletedAtIsNull(10L, 1L))
                .thenReturn(Optional.of(monitor));

        var response = monitorService.pauseMonitor(1L, 10L);

        assertThat(response.status()).isEqualTo(MonitorStatus.PAUSED);
        assertThat(monitor.getStatus()).isEqualTo(MonitorStatus.PAUSED);
    }

    @Test
    void activateMonitorChangesStatusToActiveAndRefreshesNextCheckAt() {
        Member member = member();
        Monitor monitor = monitor(member);
        monitor.pause();
        LocalDateTime previousNextCheckAt = monitor.getNextCheckAt();

        when(monitorRepository.findByIdAndMemberIdAndDeletedAtIsNull(10L, 1L))
                .thenReturn(Optional.of(monitor));

        var response = monitorService.activateMonitor(1L, 10L);

        assertThat(response.status()).isEqualTo(MonitorStatus.ACTIVE);
        assertThat(response.nextCheckAt()).isAfter(previousNextCheckAt);
    }

    @Test
    void deleteMonitorSoftDeletesMonitor() {
        Member member = member();
        Monitor monitor = monitor(member);

        when(monitorRepository.findByIdAndMemberIdAndDeletedAtIsNull(10L, 1L))
                .thenReturn(Optional.of(monitor));

        monitorService.deleteMonitor(1L, 10L);

        assertThat(monitor.getDeletedAt()).isNotNull();
        assertThat(monitor.getStatus()).isEqualTo(MonitorStatus.PAUSED);
    }

    private Member member() {
        Member member = Member.builder()
                .email("user@example.com")
                .password("password")
                .build();
        ReflectionTestUtils.setField(member, "id", 1L);
        return member;
    }

    private Monitor monitor(Member member) {
        Monitor monitor = Monitor.builder()
                .member(member)
                .name("api")
                .url("http://localhost:9999/health")
                .intervalSeconds(60)
                .timeoutMillis(1000)
                .failureThreshold(3)
                .recoveryThreshold(2)
                .status(MonitorStatus.ACTIVE)
                .nextCheckAt(LocalDateTime.of(2026, 6, 22, 12, 0))
                .build();
        ReflectionTestUtils.setField(monitor, "id", 10L);
        return monitor;
    }
}
