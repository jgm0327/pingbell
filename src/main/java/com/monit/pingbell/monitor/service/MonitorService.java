package com.monit.pingbell.monitor.service;

import com.monit.pingbell.member.domain.Member;
import com.monit.pingbell.member.repository.MemberRepository;
import com.monit.pingbell.monitor.domain.Monitor;
import com.monit.pingbell.monitor.domain.MonitorStatus;
import com.monit.pingbell.monitor.dto.MonitorRegisterRequest;
import com.monit.pingbell.monitor.dto.MonitorRegisterResponse;
import com.monit.pingbell.monitor.dto.MonitorResponse;
import com.monit.pingbell.monitor.dto.MonitorUpdateRequest;
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
        return monitorRepository.findAllByMemberIdAndDeletedAtIsNullOrderByIdDesc(memberId)
                .stream()
                .map(MonitorResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public MonitorResponse getMonitor(Long memberId, Long monitorId) {
        Monitor monitor = getOwnedMonitor(memberId, monitorId);
        return MonitorResponse.from(monitor);
    }

    @Transactional
    public MonitorResponse updateMonitor(Long memberId, Long monitorId, MonitorUpdateRequest request) {
        validateUrl(request.url());
        Monitor monitor = getOwnedMonitor(memberId, monitorId);
        LocalDateTime nextCheckAt = LocalDateTime.now().plusSeconds(request.intervalSeconds());

        monitor.update(
                request.name(),
                request.url(),
                request.intervalSeconds(),
                request.timeoutMillis(),
                request.failureThreshold(),
                request.recoveryThreshold(),
                nextCheckAt
        );
        return MonitorResponse.from(monitor);
    }

    @Transactional
    public MonitorResponse pauseMonitor(Long memberId, Long monitorId) {
        Monitor monitor = getOwnedMonitor(memberId, monitorId);
        monitor.pause();
        return MonitorResponse.from(monitor);
    }

    @Transactional
    public MonitorResponse activateMonitor(Long memberId, Long monitorId) {
        Monitor monitor = getOwnedMonitor(memberId, monitorId);
        monitor.activate(LocalDateTime.now().plusSeconds(monitor.getIntervalSeconds()));
        return MonitorResponse.from(monitor);
    }

    @Transactional
    public void deleteMonitor(Long memberId, Long monitorId) {
        Monitor monitor = getOwnedMonitor(memberId, monitorId);
        monitor.delete(LocalDateTime.now());
    }

    private Monitor getOwnedMonitor(Long memberId, Long monitorId) {
        return monitorRepository.findByIdAndMemberIdAndDeletedAtIsNull(monitorId, memberId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Monitor not found."));
    }

    private void validateUrl(String rawUrl) {
        try {
            URI uri = URI.create(rawUrl);
            String scheme = uri.getScheme();
            String host = uri.getHost();

            if (scheme == null || host == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid URL format.");
            }
            if (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only http/https URLs are allowed.");
            }
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid URL format.");
        }
    }
}
