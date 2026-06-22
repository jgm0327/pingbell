package com.monit.pingbell.notification.controller;

import com.monit.pingbell.global.common.PageResponse;
import com.monit.pingbell.global.security.auth.AuthenticatedMember;
import com.monit.pingbell.notification.dto.NotificationHistoryResponse;
import com.monit.pingbell.notification.service.NotificationHistoryQueryService;
import com.monit.pingbell.notification.service.NotificationHistoryResendService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/notification-histories")
@RequiredArgsConstructor
public class NotificationHistoryController {

    private static final int MAX_PAGE_SIZE = 100;

    private final NotificationHistoryQueryService historyQueryService;
    private final NotificationHistoryResendService historyResendService;

    @GetMapping
    public PageResponse<NotificationHistoryResponse> getHistories(
            @AuthenticationPrincipal AuthenticatedMember member,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return historyQueryService.getHistories(member.id(), PageRequest.of(
                validatePage(page),
                validateSize(size),
                Sort.by(Sort.Direction.DESC, "id")
        ));
    }

    @PostMapping("/{historyId}/resend")
    @ResponseStatus(HttpStatus.CREATED)
    public NotificationHistoryResponse resend(
            @AuthenticationPrincipal AuthenticatedMember member,
            @PathVariable Long historyId
    ) {
        return historyResendService.resend(member.id(), historyId, LocalDateTime.now());
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
