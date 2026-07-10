package ru.practicum.main.service.event.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import ru.practicum.common.dto.EventFullDto;
import ru.practicum.common.dto.EventShortDto;
import ru.practicum.main.service.event.repository.EventRepository;
import ru.practicum.main.service.event.service.EventService;

import java.util.List;

@RestController
@RequestMapping("/internal/events")
@RequiredArgsConstructor
public class InternalEventController {
    private final EventService eventService;
    private final EventRepository eventRepository;

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

    @GetMapping("/count-by-category")
    public Long countEventsByCategory(@RequestParam Long categoryId) {
        return eventRepository.countByCategoryId(categoryId);
    }
}