package com.monit.pingbell.monitor.repository;

import com.monit.pingbell.monitor.domain.Monitor;
import com.monit.pingbell.monitor.domain.MonitorStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface MonitorRepository extends JpaRepository<Monitor, Long> {
    List<Monitor> findAllByMemberIdOrderByIdDesc(Long memberId);

    Optional<Monitor> findByIdAndMemberId(Long id, Long memberId);

    List<Monitor> findAllByStatusInAndDeletedAtIsNullAndNextCheckAtLessThanEqual(List<MonitorStatus> status, LocalDateTime now);
}
