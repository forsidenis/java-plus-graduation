package ru.practicum.rating.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import ru.practicum.common.dto.EventFullDto;

@FeignClient(name = "event-service", fallback = EventClientFallback.class)
public interface EventClient {
    @GetMapping("/internal/events/{eventId}")
    EventFullDto getEvent(@PathVariable("eventId") Long eventId);
}