package ru.practicum.category.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class EventClientFallback implements EventClient {
    @Override
    public Long countEventsByCategory(Long categoryId) {
        log.warn("EventClient fallback: event-service unavailable, returning 0 for categoryId={}", categoryId);
        return 0L;
    }
}