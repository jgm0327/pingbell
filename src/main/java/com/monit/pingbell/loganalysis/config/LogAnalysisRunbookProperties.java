package com.monit.pingbell.loganalysis.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "pingbell.log-analysis.runbook")
public class LogAnalysisRunbookProperties {

    private boolean enabled = true;
    private int topK = 3;
    private double minimumRelevance = 0.5;
    private int maxContextChunks = 3;
    private int maxContextCharacters = 6000;
    private int maxQueryCharacters = 4000;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getTopK() {
        return topK;
    }

    public void setTopK(int topK) {
        this.topK = topK;
    }

    public double getMinimumRelevance() {
        return minimumRelevance;
    }

    public void setMinimumRelevance(double minimumRelevance) {
        this.minimumRelevance = minimumRelevance;
    }

    public int getMaxContextChunks() {
        return maxContextChunks;
    }

    public void setMaxContextChunks(int maxContextChunks) {
        this.maxContextChunks = maxContextChunks;
    }

    public int getMaxContextCharacters() {
        return maxContextCharacters;
    }

    public void setMaxContextCharacters(int maxContextCharacters) {
        this.maxContextCharacters = maxContextCharacters;
    }

    public int getMaxQueryCharacters() {
        return maxQueryCharacters;
    }

    public void setMaxQueryCharacters(int maxQueryCharacters) {
        this.maxQueryCharacters = maxQueryCharacters;
    }
}
