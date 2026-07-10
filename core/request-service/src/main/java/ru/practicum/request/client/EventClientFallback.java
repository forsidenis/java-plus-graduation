package ru.practicum.request.client;

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
        log.warn("EventClient fallback: event-service unavailable, returning dummy event for eventId={}", eventId);
        return EventFullDto.builder()
                .id(eventId)
                .state(EventState.PUBLISHED)
                .eventDate(LocalDateTime.now().plusDays(1))
                .participantLimit(0)
                .requestModeration(true)
                .initiator(UserShortDto.builder().id(1L).name("dummy_initiator").build())
                .build();
    }
}