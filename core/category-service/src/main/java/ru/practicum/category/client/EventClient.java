package ru.practicum.category.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "event-service", fallback = EventClientFallback.class)
public interface EventClient {
    @GetMapping("/internal/events/count-by-category")
    Long countEventsByCategory(@RequestParam("categoryId") Long categoryId);
}