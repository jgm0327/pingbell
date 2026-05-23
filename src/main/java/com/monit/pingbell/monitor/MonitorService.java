package com.monit.pingbell.monitor;

import com.monit.pingbell.monitor.dto.MonitorRegisterRequest;
import com.monit.pingbell.monitor.dto.MonitorRegisterResponse;
import com.monit.pingbell.monitor.dto.MonitorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class MonitorService {

    private final MonitorRepository monitorRepository;

    public MonitorService(MonitorRepository monitorRepository) {
        this.monitorRepository = monitorRepository;
    }

    @Transactional
    public MonitorRegisterResponse monitorUrlRegister(MonitorRegisterRequest request) {
        Long memberId = resolveAuthenticatedMemberId();
        validateUrl(request.url());

        Monitor monitor = Monitor.builder()
                .userId(memberId)
                .name(request.name())
                .url(request.url())
                .intervalSeconds(request.intervalSeconds())
                .timeoutMillis(request.timeoutMillis())
                .failureThreshold(request.failureThreshold())
                .recoveryThreshold(request.recoveryThreshold())
                .status(MonitorStatus.ACTIVE)
                .nextCheckAt(LocalDateTime.now().plusSeconds(request.intervalSeconds()))
                .build();

        Monitor saved = monitorRepository.save(monitor);
        return MonitorRegisterResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<MonitorResponse> getMonitors() {
        Long memberId = resolveAuthenticatedMemberId();
        return monitorRepository.findAllByUserIdOrderByIdDesc(memberId)
                .stream()
                .map(MonitorResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public MonitorResponse getMonitor(Long monitorId) {
        Long memberId = resolveAuthenticatedMemberId();
        Monitor monitor = monitorRepository.findByIdAndUserId(monitorId, memberId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Monitor not found."));
        return MonitorResponse.from(monitor);
    }

    private Long resolveAuthenticatedMemberId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getCredentials() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required.");
        }

        try {
            return Long.valueOf(authentication.getCredentials().toString());
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid authentication context.");
        }
    }

    private void validateUrl(String rawUrl) {
        try {
            URI uri = URI.create(rawUrl);
            String scheme = uri.getScheme();
            String host = uri.getHost();

            if (scheme == null || host == null) {
                throw new IllegalArgumentException();
            }
            if (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https")) {
                throw new IllegalArgumentException();
            }
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid URL format.");
        }
    }
}
