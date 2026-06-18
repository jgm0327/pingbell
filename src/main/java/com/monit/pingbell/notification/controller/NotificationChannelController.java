package com.monit.pingbell.notification.controller;

import com.monit.pingbell.global.security.auth.AuthenticatedMember;
import com.monit.pingbell.notification.dto.NotificationChannelCreateRequest;
import com.monit.pingbell.notification.dto.NotificationChannelResponse;
import com.monit.pingbell.notification.service.NotificationChannelService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/notification-channels")
@RequiredArgsConstructor
public class NotificationChannelController {

    private final NotificationChannelService channelService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public NotificationChannelResponse createChannel(
            @AuthenticationPrincipal AuthenticatedMember member,
            @Valid @RequestBody NotificationChannelCreateRequest request
    ) {
        return channelService.createChannel(member.id(), request);
    }

    @GetMapping
    public List<NotificationChannelResponse> getChannels(@AuthenticationPrincipal AuthenticatedMember member) {
        return channelService.getChannels(member.id());
    }
}
