package ru.practicum.main.service.event.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.practicum.common.dto.EventRatingStatsDto;

@Component
@Slf4j
public class RatingClientFallback implements RatingClient {
    @Override
    public EventRatingStatsDto getEventRatingStats(Long eventId) {
        log.warn("RatingClient fallback: возвращаем нулевую статистику для eventId={}", eventId);
        return EventRatingStatsDto.builder()
                .eventId(eventId)
                .likeCount(0L)
                .dislikeCount(0L)
                .build();
    }
}