package ru.practicum.compilation.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.practicum.common.dto.EventShortDto;

import java.util.Collections;
import java.util.List;

@Component
@Slf4j
public class EventClientFallback implements EventClient {
    @Override
    public List<EventShortDto> getEventsByIds(List<Long> ids) {
        log.warn("EventClient fallback: event-service unavailable, returning empty list for ids={}", ids);
        return Collections.emptyList();
    }
}