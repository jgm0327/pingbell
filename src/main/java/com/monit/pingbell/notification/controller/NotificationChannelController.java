package com.monit.pingbell.notification.controller;

import com.monit.pingbell.global.security.auth.AuthenticatedMember;
import com.monit.pingbell.notification.dto.NotificationChannelCreateRequest;
import com.monit.pingbell.notification.dto.NotificationChannelResponse;
import com.monit.pingbell.notification.dto.NotificationChannelTestSendResponse;
import com.monit.pingbell.notification.dto.NotificationChannelUpdateRequest;
import com.monit.pingbell.notification.service.NotificationChannelService;
import com.monit.pingbell.notification.service.NotificationChannelTestSendService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/notification-channels")
@RequiredArgsConstructor
public class NotificationChannelController {

    private final NotificationChannelService channelService;
    private final NotificationChannelTestSendService testSendService;

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

    @PatchMapping("/{publicId}")
    public NotificationChannelResponse updateChannel(
            @AuthenticationPrincipal AuthenticatedMember member,
            @PathVariable UUID publicId,
            @Valid @RequestBody NotificationChannelUpdateRequest request
    ) {
        return channelService.updateChannel(member.id(), publicId, request);
    }

    @PostMapping("/{publicId}/test-send")
    public NotificationChannelTestSendResponse sendTest(
            @AuthenticationPrincipal AuthenticatedMember member,
            @PathVariable UUID publicId
    ) {
        return testSendService.sendTest(member.id(), publicId);
    }

    @DeleteMapping("/{publicId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteChannel(
            @AuthenticationPrincipal AuthenticatedMember member,
            @PathVariable UUID publicId
    ) {
        channelService.deleteChannel(member.id(), publicId);
    }
}
