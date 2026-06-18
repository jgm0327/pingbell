package com.monit.pingbell.check.service;

import com.monit.pingbell.check.dto.CheckResultResponse;
import com.monit.pingbell.check.repository.CheckResultRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CheckResultQueryService {
    private final CheckResultRepository checkResultRepository;

    @Transactional(readOnly = true)
    public List<CheckResultResponse> getCheckResults(Long monitorId) {
        return checkResultRepository.findAllByMonitorIdOrderByCreatedAtDesc(monitorId)
                .stream()
                .map(CheckResultResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public CheckResultResponse getLatestCheckResult(Long monitorId) {
        return checkResultRepository.findFirstByMonitorIdOrderByCreatedAtDesc(monitorId)
                .map(CheckResultResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Check result not found."));
    }
}
