package com.monit.pingbell.runbook.search;

import java.util.List;
import java.util.Optional;

interface RunbookVectorSearchStore {
    List<RunbookSearchCandidate> search(Long tenantId, RunbookSearchCriteria criteria,
                                        String modelName, double[] queryVector);

    Optional<RunbookSearchCandidate> findActiveChunk(Long tenantId, String documentId,
                                                      int version, String chunkId);
}
