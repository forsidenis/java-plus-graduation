package ru.practicum.main.service.event.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import ru.practicum.common.dto.EventRatingStatsDto;

@FeignClient(name = "rating-service", fallback = RatingClientFallback.class)
public interface RatingClient {
    @GetMapping("/internal/ratings/{eventId}/stats")
    EventRatingStatsDto getEventRatingStats(@PathVariable("eventId") Long eventId);
}