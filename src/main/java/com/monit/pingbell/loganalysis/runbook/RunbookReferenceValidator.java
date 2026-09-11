package com.monit.pingbell.loganalysis.runbook;

import com.monit.pingbell.loganalysis.dto.RunbookReferenceResponse;
import com.monit.pingbell.runbook.search.RunbookVectorSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns the model's raw cited chunk ids into the {@code references} shown to the user.
 * <p>
 * A cited id is only accepted when it (1) matches a chunk that was actually included in the prompt
 * context - never a hallucinated id - and (2) still resolves to the Tenant's current ACTIVE/latest
 * revision at validation time, which defends against a document being retired or superseded between
 * context build and response validation. Everything else is silently dropped rather than failing the
 * whole analysis.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RunbookReferenceValidator {

    private final RunbookVectorSearchService searchService;

    public List<RunbookReferenceResponse> validate(Long tenantId, List<RunbookContextChunk> providedContext,
                                                    List<String> citedChunkIds) {
        if (tenantId == null || citedChunkIds == null || citedChunkIds.isEmpty()
                || providedContext == null || providedContext.isEmpty()) {
            return List.of();
        }

        Map<String, RunbookContextChunk> providedByChunkId = new LinkedHashMap<>();
        for (RunbookContextChunk chunk : providedContext) {
            providedByChunkId.putIfAbsent(chunk.chunkId(), chunk);
        }

        Map<String, RunbookReferenceResponse> validated = new LinkedHashMap<>();
        for (String citedChunkId : citedChunkIds) {
            RunbookContextChunk chunk = providedByChunkId.get(citedChunkId);
            if (chunk == null) {
                continue;
            }
            if (!isStillActive(tenantId, chunk)) {
                continue;
            }
            String key = chunk.documentId() + "@" + chunk.version();
            validated.putIfAbsent(key, new RunbookReferenceResponse(chunk.documentId(), chunk.title(), chunk.version()));
        }
        return List.copyOf(validated.values());
    }

    private boolean isStillActive(Long tenantId, RunbookContextChunk chunk) {
        try {
            return searchService.findActiveChunk(tenantId, chunk.documentId(), chunk.version(), chunk.chunkId())
                    .isPresent();
        } catch (RuntimeException e) {
            log.warn("Runbook reference revalidation failed for tenant {} document {}; dropping reference.",
                    tenantId, chunk.documentId(), e);
            return false;
        }
    }
}
