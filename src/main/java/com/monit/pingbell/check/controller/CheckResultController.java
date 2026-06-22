package com.monit.pingbell.check.controller;

import com.monit.pingbell.check.dto.CheckResultResponse;
import com.monit.pingbell.check.service.CheckResultQueryService;
import com.monit.pingbell.global.common.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/monitors/{monitorId}/checks")
@RequiredArgsConstructor
public class CheckResultController {

    private static final int MAX_PAGE_SIZE = 100;

    private final CheckResultQueryService checkResultQueryService;

    @GetMapping
    public PageResponse<CheckResultResponse> getCheckResults(
            @PathVariable Long monitorId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return checkResultQueryService.getCheckResults(monitorId, PageRequest.of(validatePage(page), validateSize(size)));
    }

    @GetMapping("/latest")
    public CheckResultResponse getLatestCheckResult(@PathVariable Long monitorId) {
        return checkResultQueryService.getLatestCheckResult(monitorId);
    }

    private int validatePage(int page) {
        if (page < 0) {
            throw new IllegalArgumentException("page must be greater than or equal to 0");
        }
        return page;
    }

    private int validateSize(int size) {
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("size must be between 1 and " + MAX_PAGE_SIZE);
        }
        return size;
    }
}
