package com.monit.pingbell.logingestion.service;

import com.monit.pingbell.logingestion.config.LogIngestionProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LogIngestionConfigTemplateServiceTest {

    @Test
    void fluentBitConfigFillsInMonitorUrlAndKeyLeavingOnlyLogPathForTheUser() {
        LogIngestionProperties properties = new LogIngestionProperties();
        properties.setPublicBaseUrl("http://localhost:8080");
        LogIngestionConfigTemplateService service = new LogIngestionConfigTemplateService(properties);

        String config = service.fluentBitConfig(12L, "pgbl_secret-key");

        assertThat(config)
                .contains("Path         /path/to/your/app.log")
                .contains("Tag          pingbell.monitor-12")
                .contains("Host         localhost")
                .contains("Port         8080")
                .contains("URI          /api/v1/monitors/12/logs")
                .contains("tls          Off")
                .contains("Header       Authorization Bearer pgbl_secret-key");
    }

    @Test
    void fluentBitConfigEnablesTlsForHttpsBaseUrlAndDefaultsToPort443() {
        LogIngestionProperties properties = new LogIngestionProperties();
        properties.setPublicBaseUrl("https://pingbell.example.com");
        LogIngestionConfigTemplateService service = new LogIngestionConfigTemplateService(properties);

        String config = service.fluentBitConfig(7L, "pgbl_key");

        assertThat(config)
                .contains("Host         pingbell.example.com")
                .contains("Port         443")
                .contains("tls          On");
    }

    @Test
    void dockerComposeSnippetReferencesTheGeneratedConfigFileAndTheFluentdDriverAlternative() {
        LogIngestionConfigTemplateService service = new LogIngestionConfigTemplateService(new LogIngestionProperties());

        String snippet = service.dockerComposeSnippet(12L);

        assertThat(snippet)
                .contains("fluent-bit-monitor-12")
                .contains("./fluent-bit-monitor-12.conf:/fluent-bit/etc/fluent-bit.conf:ro")
                .contains("driver: fluentd");
    }
}
