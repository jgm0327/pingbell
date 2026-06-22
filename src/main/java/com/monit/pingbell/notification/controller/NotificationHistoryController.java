package com.monit.pingbell.notification.controller;

import com.monit.pingbell.global.security.auth.AuthenticatedMember;
import com.monit.pingbell.notification.dto.NotificationHistoryResponse;
import com.monit.pingbell.notification.service.NotificationHistoryQueryService;
import com.monit.pingbell.notification.service.NotificationHistoryResendService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/notification-histories")
@RequiredArgsConstructor
public class NotificationHistoryController {

    private final NotificationHistoryQueryService historyQueryService;
    private final NotificationHistoryResendService historyResendService;

    @GetMapping
    public List<NotificationHistoryResponse> getHistories(
            @AuthenticationPrincipal AuthenticatedMember member
    ) {
        return historyQueryService.getHistories(member.id());
    }

    @PostMapping("/{historyId}/resend")
    @ResponseStatus(HttpStatus.CREATED)
    public NotificationHistoryResponse resend(
            @AuthenticationPrincipal AuthenticatedMember member,
            @PathVariable Long historyId
    ) {
        return historyResendService.resend(member.id(), historyId, LocalDateTime.now());
    }
}
