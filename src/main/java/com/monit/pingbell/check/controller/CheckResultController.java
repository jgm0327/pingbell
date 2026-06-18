package com.monit.pingbell.check.controller;

import com.monit.pingbell.check.dto.CheckResultResponse;
import com.monit.pingbell.check.service.CheckResultQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/monitors/{monitorId}/checks")
@RequiredArgsConstructor
public class CheckResultController {
    private final CheckResultQueryService checkResultQueryService;

    @GetMapping
    public List<CheckResultResponse> getCheckResults(@PathVariable Long monitorId) {
        return checkResultQueryService.getCheckResults(monitorId);
    }

    @GetMapping("/latest")
    public CheckResultResponse getLatestCheckResult(@PathVariable Long monitorId) {
        return checkResultQueryService.getLatestCheckResult(monitorId);
    }
}
