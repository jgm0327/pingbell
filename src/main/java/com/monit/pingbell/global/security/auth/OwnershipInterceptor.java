package com.monit.pingbell.global.security.auth;

import com.monit.pingbell.incident.repository.IncidentRepository;
import com.monit.pingbell.monitor.repository.MonitorRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class OwnershipInterceptor implements HandlerInterceptor {
    private final MonitorRepository monitorRepository;
    private final IncidentRepository incidentRepository;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Map<String, String> pathVariables = pathVariables(request);
        AuthenticatedMember member = authenticatedMember();

        String monitorId = pathVariables.get("monitorId");
        if (monitorId != null && !monitorRepository.existsByIdAndMemberId(Long.valueOf(monitorId), member.id())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Monitor not found.");
        }

        String incidentId = pathVariables.get("incidentId");
        if (incidentId != null && !incidentRepository.existsByIdAndMonitorMemberId(Long.valueOf(incidentId), member.id())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Incident not found.");
        }

        return true;
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> pathVariables(HttpServletRequest request) {
        Object attribute = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (attribute instanceof Map<?, ?> variables) {
            return (Map<String, String>) variables;
        }
        return Map.of();
    }

    private AuthenticatedMember authenticatedMember() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedMember member)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required.");
        }
        return member;
    }
}
