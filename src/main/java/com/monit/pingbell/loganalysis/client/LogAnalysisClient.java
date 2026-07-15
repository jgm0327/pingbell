package com.monit.pingbell.loganalysis.client;

public interface LogAnalysisClient {

    LogAnalysisClientResult analyze(String logContent, String question);
}
