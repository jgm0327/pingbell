package com.monit.pingbell.logingestion.repository;

import com.monit.pingbell.logingestion.domain.LogIngestionApiKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LogIngestionApiKeyRepository extends JpaRepository<LogIngestionApiKey, Long> {

    Optional<LogIngestionApiKey> findByKeyHash(String keyHash);

    List<LogIngestionApiKey> findAllByTenantIdAndMonitorIdOrderByCreatedAtDesc(Long tenantId, Long monitorId);

    Optional<LogIngestionApiKey> findByIdAndTenantIdAndMonitorId(Long id, Long tenantId, Long monitorId);
}
