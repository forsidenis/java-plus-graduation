package ru.practicum.rating.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.practicum.common.dto.EventRatingStatsDto;
import ru.practicum.rating.service.RatingService;

@RestController
@RequestMapping("/internal/ratings")
@RequiredArgsConstructor
public class InternalRatingController {
    private final RatingService ratingService;

    @GetMapping("/{eventId}/stats")
    public EventRatingStatsDto getEventRatingStats(@PathVariable Long eventId) {
        return ratingService.getEventRatingStats(eventId);
    }
}