package com.monit.pingbell.runbook.embedding;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EmbeddingClientConfig {
    @Bean
    @ConditionalOnMissingBean(EmbeddingClient.class)
    EmbeddingClient unavailableEmbeddingClient() {
        return inputs -> {
            throw new EmbeddingClientException("No external embedding client is configured.");
        };
    }
}
