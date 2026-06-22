package com.monit.pingbell.notification.controller;

import com.monit.pingbell.global.security.auth.AuthenticatedMember;
import com.monit.pingbell.notification.dto.NotificationHistoryResponse;
import com.monit.pingbell.notification.service.NotificationHistoryQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/notification-histories")
@RequiredArgsConstructor
public class NotificationHistoryController {

    private final NotificationHistoryQueryService historyQueryService;

    @GetMapping
    public List<NotificationHistoryResponse> getHistories(
            @AuthenticationPrincipal AuthenticatedMember member
    ) {
        return historyQueryService.getHistories(member.id());
    }
}
