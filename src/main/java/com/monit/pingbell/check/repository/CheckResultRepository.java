package com.monit.pingbell.check.repository;

import com.monit.pingbell.check.domain.CheckResult;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CheckResultRepository extends JpaRepository<CheckResult, Long> {

}
