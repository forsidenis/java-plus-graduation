package ru.practicum.rating.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.practicum.common.dto.EventFullDto;
import ru.practicum.common.dto.EventState;
import ru.practicum.common.dto.UserShortDto;

import java.time.LocalDateTime;

@Component
@Slf4j
public class EventClientFallback implements EventClient {
    @Override
    public EventFullDto getEvent(Long eventId) {
        log.warn("EventClient fallback: возвращаем заглушку для eventId={}", eventId);
        return EventFullDto.builder()
                .id(eventId)
                .state(EventState.PUBLISHED)
                .eventDate(LocalDateTime.of(2000, 1, 1, 0, 0, 0))
                .participantLimit(0)
                .requestModeration(true)
                .initiator(UserShortDto.builder()
                        .id(1L)
                        .name("dummy_initiator")
                        .build())
                .build();
    }
}