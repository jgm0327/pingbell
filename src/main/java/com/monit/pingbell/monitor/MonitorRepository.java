package com.monit.pingbell.monitor;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface MonitorRepository extends JpaRepository<Monitor, Long> {
    List<Monitor> findAllByUserIdOrderByIdDesc(Long userId);

    Optional<Monitor> findByIdAndUserId(Long id, Long userId);

    List<Monitor> findAllByStatusAndDeletedAtIsNullAndNextCheckAtLessThanEqual(MonitorStatus status, LocalDateTime now);
}
