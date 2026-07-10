package ru.practicum.rating.service;

import ru.practicum.common.dto.EventRatingListDto;
import ru.practicum.common.dto.EventRatingRequestDto;
import ru.practicum.common.dto.EventRatingResponseDto;
import ru.practicum.common.dto.EventRatingStatsDto;
import ru.practicum.common.dto.EventRatingTopDto;

import java.util.List;

public interface RatingService {
    EventRatingResponseDto addLike(Long userId, Long eventId, EventRatingRequestDto dto);

    EventRatingResponseDto addDislike(Long userId, Long eventId, EventRatingRequestDto dto);

    void deleteRating(Long userId, Long eventId);

    EventRatingStatsDto getEventRatingStats(Long eventId);

    EventRatingListDto getUserRatings(Long userId, String rating, int from, int size);

    List<EventRatingTopDto> getTopRatedEvents(int from, int size, String order);
}