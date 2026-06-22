package com.monit.pingbell.check.repository;

import com.monit.pingbell.check.domain.CheckResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CheckResultRepository extends JpaRepository<CheckResult, Long> {
    List<CheckResult> findAllByMonitorIdAndMonitorMemberIdOrderByCreatedAtDesc(Long monitorId, Long memberId);

    Optional<CheckResult> findFirstByMonitorIdAndMonitorMemberIdOrderByCreatedAtDesc(Long monitorId, Long memberId);

    Page<CheckResult> findAllByMonitorIdOrderByIdDesc(Long monitorId, Pageable pageable);

    Optional<CheckResult> findFirstByMonitorIdOrderByCreatedAtDesc(Long monitorId);
}
