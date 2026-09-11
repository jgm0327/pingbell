package com.monit.pingbell.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfig {

    // Backs automatic Incident log analysis (IncidentLogAnalysisTriggerService) so a slow LLM
    // call never blocks Incident detection or notification dispatch. Bounded on purpose - this
    // calls an external API, so an unbounded executor could pile up runaway threads under load.
    @Bean(name = "logAnalysisExecutor")
    public Executor logAnalysisExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("log-analysis-async-");
        executor.initialize();
        return executor;
    }
}
