package com.monit.pingbell.runbook.dto;

import com.monit.pingbell.runbook.domain.RunbookDocumentType;
import com.monit.pingbell.runbook.domain.RunbookStatus;
import jakarta.validation.constraints.*;
import java.util.List;

public record RunbookCreateRequest(
        @NotBlank @Size(max = 100)
        @Pattern(regexp = "[a-z0-9][a-z0-9-]*", message = "must contain normalized lowercase letters, numbers, and hyphens")
        String documentId,
        @NotBlank @Size(max = 200) String title,
        @NotNull RunbookDocumentType documentType,
        @NotBlank @Size(max = 100)
        @Pattern(regexp = "[a-z0-9][a-z0-9-]*", message = "must contain normalized lowercase letters, numbers, and hyphens")
        String serviceName,
        @NotEmpty @Size(max = 20) List<
                @NotBlank @Size(max = 100)
                @Pattern(regexp = "[A-Z][A-Z0-9_]*", message = "must be an uppercase normalized value") String> errorTypes,
        @NotNull RunbookStatus status,
        @NotBlank @Size(max = 100_000) String content
) {
    public RunbookRevisionRequest revision() {
        return new RunbookRevisionRequest(title, documentType, serviceName, errorTypes, status, content);
    }
}
