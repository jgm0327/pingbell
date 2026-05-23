package com.monit.pingbell.monitor;

import com.monit.pingbell.monitor.dto.MonitorRegisterRequest;
import com.monit.pingbell.monitor.dto.MonitorRegisterResponse;
import com.monit.pingbell.monitor.dto.MonitorResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.http.HttpStatus;
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
    public MonitorRegisterResponse monitorUrlRegister(@Valid @RequestBody MonitorRegisterRequest request) {
        return monitorService.monitorUrlRegister(request);
    }

    @GetMapping
    public List<MonitorResponse> getMonitors() {
        return monitorService.getMonitors();
    }

    @GetMapping("/{monitorId}")
    public MonitorResponse getMonitor(@PathVariable Long monitorId) {
        return monitorService.getMonitor(monitorId);
    }
}
