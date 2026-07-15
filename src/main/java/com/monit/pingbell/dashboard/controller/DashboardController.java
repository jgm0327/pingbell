package com.monit.pingbell.dashboard.controller;

import com.monit.pingbell.dashboard.dto.OperationsSummaryResponse;
import com.monit.pingbell.dashboard.service.DashboardOperationsService;
import com.monit.pingbell.global.security.auth.AuthenticatedMember;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardOperationsService dashboardOperationsService;

    @GetMapping("/operations")
    public OperationsSummaryResponse getOperationsSummary(@AuthenticationPrincipal AuthenticatedMember member) {
        return dashboardOperationsService.getOperationsSummary(member.id(), LocalDateTime.now());
    }
}
