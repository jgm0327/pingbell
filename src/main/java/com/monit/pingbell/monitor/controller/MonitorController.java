package com.monit.pingbell.monitor.controller;

import com.monit.pingbell.global.security.auth.AuthenticatedMember;
import com.monit.pingbell.monitor.dto.MonitorRegisterRequest;
import com.monit.pingbell.monitor.dto.MonitorRegisterResponse;
import com.monit.pingbell.monitor.dto.MonitorResponse;
import com.monit.pingbell.monitor.dto.MonitorUpdateRequest;
import com.monit.pingbell.monitor.service.MonitorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/monitors")
@RequiredArgsConstructor
public class MonitorController {

    private final MonitorService monitorService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MonitorRegisterResponse monitorUrlRegister(
            @AuthenticationPrincipal AuthenticatedMember member,
            @Valid @RequestBody MonitorRegisterRequest request
    ) {
        return monitorService.monitorUrlRegister(member.id(), request);
    }

    @GetMapping
    public List<MonitorResponse> getMonitors(@AuthenticationPrincipal AuthenticatedMember member) {
        return monitorService.getMonitors(member.id());
    }

    @GetMapping("/{monitorId}")
    public MonitorResponse getMonitor(
            @AuthenticationPrincipal AuthenticatedMember member,
            @PathVariable Long monitorId
    ) {
        return monitorService.getMonitor(member.id(), monitorId);
    }

    @PatchMapping("/{monitorId}")
    public MonitorResponse updateMonitor(
            @AuthenticationPrincipal AuthenticatedMember member,
            @PathVariable Long monitorId,
            @Valid @RequestBody MonitorUpdateRequest request
    ) {
        return monitorService.updateMonitor(member.id(), monitorId, request);
    }

    @PatchMapping("/{monitorId}/pause")
    public MonitorResponse pauseMonitor(
            @AuthenticationPrincipal AuthenticatedMember member,
            @PathVariable Long monitorId
    ) {
        return monitorService.pauseMonitor(member.id(), monitorId);
    }

    @PatchMapping("/{monitorId}/activate")
    public MonitorResponse activateMonitor(
            @AuthenticationPrincipal AuthenticatedMember member,
            @PathVariable Long monitorId
    ) {
        return monitorService.activateMonitor(member.id(), monitorId);
    }

    @DeleteMapping("/{monitorId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMonitor(
            @AuthenticationPrincipal AuthenticatedMember member,
            @PathVariable Long monitorId
    ) {
        monitorService.deleteMonitor(member.id(), monitorId);
    }
}
