package com.monit.pingbell.check.client;

import lombok.RequiredArgsConstructor;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@RequiredArgsConstructor
public class HealthCheckClient {
    private final RestClient.Builder restClient;

    public int check(String url, int timeout) {
        HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory();
        requestFactory.setReadTimeout(timeout);
        requestFactory.setConnectionRequestTimeout(timeout);

        RestClient client = restClient.build();

        return client
                .get()
                .uri(url)
                .exchange((req, res) -> res.getStatusCode().value());

    }
}