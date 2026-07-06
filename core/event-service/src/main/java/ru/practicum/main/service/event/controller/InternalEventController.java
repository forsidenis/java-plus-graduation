package ru.practicum.main.service.event.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import ru.practicum.common.dto.EventFullDto;
import ru.practicum.common.dto.EventShortDto;
import ru.practicum.main.service.event.service.EventService;

import java.util.List;

@RestController
@RequestMapping("/internal/events")
@RequiredArgsConstructor
public class InternalEventController {
    private final EventService eventService;

    @GetMapping("/{eventId}")
    public EventFullDto getEvent(@PathVariable Long eventId) {
        return eventService.getEventInternal(eventId);
    }

    @GetMapping("/by-ids")
    public List<EventShortDto> getEventsByIds(@RequestParam List<Long> ids) {
        return eventService.getEventsByIdsInternal(ids);
    }

    @GetMapping("/{eventId}/check-published")
    public boolean isPublished(@PathVariable Long eventId) {
        return eventService.isEventPublished(eventId);
    }
}