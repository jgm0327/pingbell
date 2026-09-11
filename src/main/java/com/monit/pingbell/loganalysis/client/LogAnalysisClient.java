package com.monit.pingbell.loganalysis.client;

import com.monit.pingbell.loganalysis.runbook.RunbookContextChunk;

import java.util.List;

public interface LogAnalysisClient {

    LogAnalysisClientResult analyze(String logContent, String question, List<RunbookContextChunk> runbookContext);
}
