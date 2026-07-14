package com.monit.pingbell.loganalysis.controller;

import com.monit.pingbell.global.security.auth.AuthenticatedMember;
import com.monit.pingbell.loganalysis.dto.LogAnalysisRequest;
import com.monit.pingbell.loganalysis.dto.LogAnalysisResponse;
import com.monit.pingbell.loganalysis.service.LogAnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/monitors/{monitorId}/log-analyses")
@RequiredArgsConstructor
public class LogAnalysisController {

    private final LogAnalysisService logAnalysisService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public LogAnalysisResponse analyze(
            @AuthenticationPrincipal AuthenticatedMember member,
            @PathVariable Long monitorId,
            @ModelAttribute LogAnalysisRequest request
    ) {
        return logAnalysisService.analyze(member.id(), monitorId, request);
    }
}
