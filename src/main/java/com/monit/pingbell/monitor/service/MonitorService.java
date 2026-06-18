package com.monit.pingbell.monitor.service;

import com.monit.pingbell.member.domain.Member;
import com.monit.pingbell.member.repository.MemberRepository;
import com.monit.pingbell.monitor.domain.Monitor;
import com.monit.pingbell.monitor.domain.MonitorStatus;
import com.monit.pingbell.monitor.dto.MonitorRegisterRequest;
import com.monit.pingbell.monitor.dto.MonitorRegisterResponse;
import com.monit.pingbell.monitor.dto.MonitorResponse;
import com.monit.pingbell.monitor.repository.MonitorRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class MonitorService {

    private final MonitorRepository monitorRepository;
    private final MemberRepository memberRepository;

    public MonitorService(MonitorRepository monitorRepository, MemberRepository memberRepository) {
        this.monitorRepository = monitorRepository;
        this.memberRepository = memberRepository;
    }

    @Transactional
    public MonitorRegisterResponse monitorUrlRegister(Long memberId, MonitorRegisterRequest request) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid authentication context."));
        validateUrl(request.url());

        Monitor monitor = Monitor.builder()
                .member(member)
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
    public List<MonitorResponse> getMonitors(Long memberId) {
        return monitorRepository.findAllByMemberIdOrderByIdDesc(memberId)
                .stream()
                .map(MonitorResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public MonitorResponse getMonitor(Long monitorId) {
        Monitor monitor = monitorRepository.findById(monitorId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Monitor not found."));
        return MonitorResponse.from(monitor);
    }

    private void validateUrl(String rawUrl) {
        try {
            URI uri = URI.create(rawUrl);
            String scheme = uri.getScheme();
            String host = uri.getHost();

            if (scheme == null || host == null) {
                throw new IllegalArgumentException("Invalid URL format.");
            }
            if (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https")) {
                throw new IllegalArgumentException("Only http/https URLs are allowed.");
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid URL format.");
        }
    }
}
