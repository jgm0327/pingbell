package com.monit.pingbell.runbook.dto;

import com.monit.pingbell.runbook.domain.RunbookStatus;
import jakarta.validation.constraints.NotNull;

public record RunbookStatusUpdateRequest(@NotNull RunbookStatus status) {}
