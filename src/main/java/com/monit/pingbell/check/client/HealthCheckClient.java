package com.monit.pingbell.check.client;

import lombok.RequiredArgsConstructor;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.util.Timeout;
import com.monit.pingbell.global.exception.HealthCheckClientException;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class HealthCheckClient {
    private final RestClient.Builder restClient;

    public int check(String url, int timeout) {
        ConnectionConfig config = ConnectionConfig.custom()
                .setConnectTimeout(Timeout.ofMilliseconds(timeout))
                .build();

        RequestConfig requestConfig = RequestConfig.custom()
                .setResponseTimeout(Timeout.ofMilliseconds(timeout))
                .setConnectionRequestTimeout(Timeout.ofMilliseconds(timeout))
                .build();

        try (PoolingHttpClientConnectionManager connectionManager = PoolingHttpClientConnectionManagerBuilder.create()
                .setDefaultConnectionConfig(config)
                .build();
             CloseableHttpClient httpClient = HttpClients.custom()
                     .setDefaultRequestConfig(requestConfig)
                     .setConnectionManager(connectionManager)
                     .build()) {
            HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory(httpClient);
            RestClient client = restClient
                    .requestFactory(requestFactory)
                    .build();

            Integer statusCode = client
                    .get()
                    .uri(url)
                    .exchange((req, res) -> res.getStatusCode().value());

            if (statusCode == null) {
                throw new IllegalStateException("HTTP status code is null");
            }
            return statusCode;
        } catch (IOException e) {
            throw new HealthCheckClientException("Failed to close HTTP client resources", e);
        }
    }
}
