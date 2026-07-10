package ru.practicum.rating.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "request-service", fallback = RequestClientFallback.class)
public interface RequestClient {
    @GetMapping("/internal/requests/exists/{eventId}/{userId}")
    boolean existsByEventAndUserAndStatusConfirmed(@PathVariable("eventId") Long eventId,
                                                   @PathVariable("userId") Long userId);
}