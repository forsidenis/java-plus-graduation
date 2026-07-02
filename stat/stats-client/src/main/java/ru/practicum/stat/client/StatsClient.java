package ru.practicum.stat.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.retry.backoff.FixedBackOffPolicy;
import org.springframework.retry.policy.MaxAttemptsRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import ru.practicum.stat.dto.EndpointHitDto;
import ru.practicum.stat.dto.ViewStatsDto;

import java.net.URI;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Component
@Slf4j
public class StatsClient {
    private final RestClient restClient;
    private final DiscoveryClient discoveryClient;
    private final String statsServiceId;
    private final RetryTemplate retryTemplate;

    public StatsClient(DiscoveryClient discoveryClient,
                       @Value("${stats.service.id:stats-server}") String statsServiceId) {
        this.discoveryClient = discoveryClient;
        this.statsServiceId = statsServiceId;

        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(Duration.ofSeconds(5))
                .withReadTimeout(Duration.ofSeconds(10));

        this.restClient = RestClient.builder()
                .requestFactory(ClientHttpRequestFactories.get(settings))
                .build();

        RetryTemplate retryTemplate = new RetryTemplate();
        FixedBackOffPolicy backOffPolicy = new FixedBackOffPolicy();
        backOffPolicy.setBackOffPeriod(3000L);
        retryTemplate.setBackOffPolicy(backOffPolicy);
        MaxAttemptsRetryPolicy retryPolicy = new MaxAttemptsRetryPolicy(3);
        retryTemplate.setRetryPolicy(retryPolicy);
        this.retryTemplate = retryTemplate;
    }

    private ServiceInstance getInstance() {
        List<ServiceInstance> instances = discoveryClient.getInstances(statsServiceId);
        if (instances.isEmpty()) {
            throw new RuntimeException("Нет доступных экземпляров сервиса статистики с id: " + statsServiceId);
        }
        return instances.get(0);
    }

    private URI makeUri(String path) {
        ServiceInstance instance = retryTemplate.execute(ctx -> getInstance());
        return URI.create("http://" + instance.getHost() + ":" + instance.getPort() + path);
    }

    public EndpointHitDto hit(EndpointHitDto hit) {
        URI uri = makeUri("/hit");
        return restClient.post()
                .uri(uri)
                .body(hit)
                .retrieve()
                .body(EndpointHitDto.class);
    }

    public List<ViewStatsDto> getStats(LocalDateTime start, LocalDateTime end,
                                       List<String> uris, Boolean unique) {
        URI uri = makeUri("/stats");
        UriComponentsBuilder builder = UriComponentsBuilder.fromUri(uri)
                .queryParam("start", start.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                .queryParam("end", end.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                .queryParam("unique", unique);
        if (uris != null && !uris.isEmpty()) {
            builder.queryParam("uris", String.join(",", uris));
        }
        String uriString = builder.build().toUriString();

        ViewStatsDto[] response = restClient.get()
                .uri(uriString)
                .retrieve()
                .body(ViewStatsDto[].class);
        return response != null ? Arrays.asList(response) : Collections.emptyList();
    }
}