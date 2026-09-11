package com.monit.pingbell.logingestion.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "pingbell.log-ingestion")
public class LogIngestionProperties {

    private int bufferMaxLines = 500;
    private int bufferTtlSeconds = 900;
    private int maxRequestBytes = 524_288;
    private int maxRequestsPerMinute = 60;
    // Used only to build the ready-to-use Fluent Bit config/compose snippet handed back at API
    // key issuance time (LogIngestionConfigTemplateService) - never used for request routing.
    private String publicBaseUrl = "http://localhost:8080";

    public int getBufferMaxLines() {
        return bufferMaxLines;
    }

    public void setBufferMaxLines(int bufferMaxLines) {
        this.bufferMaxLines = bufferMaxLines;
    }

    public int getBufferTtlSeconds() {
        return bufferTtlSeconds;
    }

    public void setBufferTtlSeconds(int bufferTtlSeconds) {
        this.bufferTtlSeconds = bufferTtlSeconds;
    }

    public int getMaxRequestBytes() {
        return maxRequestBytes;
    }

    public void setMaxRequestBytes(int maxRequestBytes) {
        this.maxRequestBytes = maxRequestBytes;
    }

    public int getMaxRequestsPerMinute() {
        return maxRequestsPerMinute;
    }

    public void setMaxRequestsPerMinute(int maxRequestsPerMinute) {
        this.maxRequestsPerMinute = maxRequestsPerMinute;
    }

    public String getPublicBaseUrl() {
        return publicBaseUrl;
    }

    public void setPublicBaseUrl(String publicBaseUrl) {
        this.publicBaseUrl = publicBaseUrl;
    }
}
