package com.monit.pingbell.monitor.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record MonitorRegisterRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(max = 2048) String url,
        @NotNull @Min(10) @Max(3600) Integer intervalSeconds,
        @NotNull @Min(100) @Max(30000) Integer timeoutMillis,
        @NotNull @Min(1) @Max(20) Integer failureThreshold,
        @NotNull @Min(1) @Max(20) Integer recoveryThreshold
) {
}
