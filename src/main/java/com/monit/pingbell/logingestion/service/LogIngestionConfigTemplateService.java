package com.monit.pingbell.logingestion.service;

import com.monit.pingbell.logingestion.config.LogIngestionProperties;
import org.springframework.stereotype.Service;

import java.net.URI;

/**
 * Builds ready-to-use Fluent Bit config/compose snippets with the Monitor id, ingestion URL and
 * (freshly issued, one-time-visible) API key already filled in, so the only thing left for the
 * user to edit is their own log file path. Called only at API key issuance time
 * (LogIngestionApiKeyService.issue) since that is the one moment the raw key is ever available -
 * it is never stored, so this can't be regenerated for an existing key afterward.
 */
@Service
public class LogIngestionConfigTemplateService {

    private final LogIngestionProperties properties;

    public LogIngestionConfigTemplateService(LogIngestionProperties properties) {
        this.properties = properties;
    }

    public String fluentBitConfig(Long monitorId, String apiKey) {
        URI baseUri = URI.create(properties.getPublicBaseUrl());
        boolean tls = "https".equalsIgnoreCase(baseUri.getScheme());
        int port = baseUri.getPort() != -1 ? baseUri.getPort() : (tls ? 443 : 80);
        String host = baseUri.getHost() != null ? baseUri.getHost() : "localhost";

        return """
                [SERVICE]
                    Flush        5
                    Log_Level    info

                [INPUT]
                    Name         tail
                    Path         /path/to/your/app.log
                    Tag          pingbell.monitor-%d

                [OUTPUT]
                    Name         http
                    Match        pingbell.monitor-%d
                    Host         %s
                    Port         %d
                    URI          /api/v1/monitors/%d/logs
                    Format       json_lines
                    tls          %s
                    Header       Authorization Bearer %s
                """.formatted(monitorId, monitorId, host, port, monitorId, tls ? "On" : "Off", apiKey);
    }

    // No secret in here on purpose - the compose file only mounts the .conf file that carries it.
    public String dockerComposeSnippet(Long monitorId) {
        return """
                # docker compose에 추가하세요. -f 옵션으로 별도 파일에 두고 합쳐도 됩니다.
                services:
                  fluent-bit-monitor-%d:
                    image: fluent/fluent-bit:3.1
                    volumes:
                      - ./fluent-bit-monitor-%d.conf:/fluent-bit/etc/fluent-bit.conf:ro
                      # 위 fluentBitConfig의 [INPUT] Path와 여기 마운트 경로를 실제 로그 위치에 맞게 바꾸세요.
                      - /path/to/your/app/logs:/var/log/app:ro
                    restart: unless-stopped

                # 이미 Docker로 떠 있는 앱이 파일이 아니라 stdout에만 로그를 찍는다면, 로그 경로를 몰라도
                # 되는 fluentd logging driver 방식을 대신 쓸 수 있습니다:
                #
                # services:
                #   your-app:
                #     logging:
                #       driver: fluentd
                #       options:
                #         fluentd-address: localhost:24224
                #
                # 이 경우 위 fluent-bit-monitor-%d 서비스의 [INPUT]을 다음으로 바꾸세요:
                #   [INPUT]
                #       Name    forward
                #       Listen  0.0.0.0
                #       Port    24224
                """.formatted(monitorId, monitorId, monitorId);
    }
}
