package com.monit.pingbell.check.service;

import com.monit.pingbell.check.dto.CheckResultResponse;
import com.monit.pingbell.check.repository.CheckResultRepository;
import com.monit.pingbell.global.common.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class CheckResultQueryService {
    private final CheckResultRepository checkResultRepository;

    @Transactional(readOnly = true)
    public PageResponse<CheckResultResponse> getCheckResults(Long monitorId, Pageable pageable) {
        return PageResponse.from(
                checkResultRepository.findAllByMonitorIdOrderByIdDesc(monitorId, pageable)
                        .map(CheckResultResponse::from)
        );
    }

    @Transactional(readOnly = true)
    public CheckResultResponse getLatestCheckResult(Long monitorId) {
        return checkResultRepository.findFirstByMonitorIdOrderByCreatedAtDesc(monitorId)
                .map(CheckResultResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Check result not found."));
    }
}
