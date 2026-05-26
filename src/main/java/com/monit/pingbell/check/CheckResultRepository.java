package com.monit.pingbell.check;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CheckResultRepository extends JpaRepository<CheckResult, Long> {

}
