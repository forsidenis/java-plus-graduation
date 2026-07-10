package ru.practicum.category.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class EventClientFallback implements EventClient {
    @Override
    public Long countEventsByCategory(Long categoryId) {
        log.error("EventClient fallback: event-service unavailable, throwing exception for categoryId={}", categoryId);
        throw new RuntimeException("Event service is unavailable, cannot check events count");
    }
}