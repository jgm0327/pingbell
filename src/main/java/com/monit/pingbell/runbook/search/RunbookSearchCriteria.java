package com.monit.pingbell.runbook.search;

import com.monit.pingbell.runbook.domain.RunbookDocumentType;

import java.util.List;

public record RunbookSearchCriteria(
        String query,
        String serviceName,
        List<String> errorTypes,
        RunbookDocumentType documentType,
        int topK,
        double minimumRelevance
) {
    public static final int MAX_TOP_K = 20;

    public RunbookSearchCriteria {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("query must not be blank");
        }
        if (topK < 1 || topK > MAX_TOP_K) {
            throw new IllegalArgumentException("topK must be between 1 and " + MAX_TOP_K);
        }
        if (!Double.isFinite(minimumRelevance) || minimumRelevance < 0.0 || minimumRelevance > 1.0) {
            throw new IllegalArgumentException("minimumRelevance must be between 0.0 and 1.0");
        }
        serviceName = normalizeOptional(serviceName);
        errorTypes = errorTypes == null ? List.of() : errorTypes.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::strip)
                .distinct()
                .toList();
    }

    private static String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
