package ru.practicum.event.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import ru.practicum.dto.categoryDto.CategoryDto;
import ru.practicum.dto.eventDto.EventFullDto;
import ru.practicum.dto.eventDto.EventShortDto;
import ru.practicum.dto.userDto.UserShortDto;
import ru.practicum.event.mapper.EventMapper;
import ru.practicum.event.model.Event;
import ru.practicum.event.service.PublicEventService;
import ru.practicum.faign.CategoryApiClient;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/events")
@RequiredArgsConstructor
@Validated
@Slf4j
public class PublicEventController {

    private final PublicEventService publicEventService;
    private final CategoryApiClient categoryApiClient;

    @GetMapping
    public List<EventShortDto> getEvents(@RequestParam(required = false) String text,
                                         @RequestParam(required = false) List<Long> categories,
                                         @RequestParam(required = false) Boolean paid,
                                         @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime rangeStart,
                                         @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime rangeEnd,
                                         @RequestParam(defaultValue = "false") Boolean onlyAvailable,
                                         @RequestParam(required = false) String sort,
                                         @RequestParam(defaultValue = "0") int from,
                                         @RequestParam(defaultValue = "10") int size,
                                         HttpServletRequest request) {
        log.info("GET /events - публичный поиск событий");

        List<Event> events = publicEventService.getPublicEvents(text, categories, paid, rangeStart,
                rangeEnd, onlyAvailable, sort, from, size, request);

        if (events.isEmpty()) return List.of();

        Map<Long, Long> confirmedMap = publicEventService.getConfirmedRequestsCounts(
                events.stream().map(Event::getId).toList()
        );
        Map<Long, Long> viewsMap = publicEventService.getViewsForEvents(events);
        Map<Long, UserShortDto> initiatorMap = publicEventService.getEventInitiators(events);

        // Получаем категории для всех событий
        List<Long> categoryIds = events.stream().map(Event::getCategoryId).distinct().toList();
        Map<Long, CategoryDto> categoryMap = categoryApiClient.getCategories(0, 1000).stream()
                .collect(Collectors.toMap(CategoryDto::getId, c -> c));

        return events.stream()
                .map(event -> {
                    Long confirmedRequests = confirmedMap.getOrDefault(event.getId(), 0L);
                    Long views = viewsMap.getOrDefault(event.getId(), 0L);
                    UserShortDto initiator = initiatorMap.get(event.getInitiatorId());
                    CategoryDto category = categoryMap.get(event.getCategoryId());
                    return EventMapper.toShortDto(event, confirmedRequests, views, initiator, category);
                })
                .collect(Collectors.toList());
    }

    @GetMapping("/{id}")
    public EventFullDto getEventById(@PathVariable @Positive Long id, HttpServletRequest request) {
        log.info("GET /events/{} - получение события", id);
        Event event = publicEventService.getPublicEventById(id, request);
        Long confirmedRequests = publicEventService.getConfirmedRequestsCount(id);
        Long views = publicEventService.getViewsForEvent(event);
        UserShortDto initiator = publicEventService.getEventInitiator(event);
        CategoryDto category = categoryApiClient.getCategory(event.getCategoryId());
        return EventMapper.toFullDto(event, confirmedRequests, views, initiator, category);
    }

    @GetMapping("/{id}/WithoutHttp")
    public EventFullDto getEventByIdWithoutHttp(@PathVariable @Positive Long id) {
        log.info("GET /events/{} - получение события без учёта в статистику", id);
        Event event = publicEventService.getPublicEventByIdWithoutHttp(id);
        Long confirmedRequests = publicEventService.getConfirmedRequestsCount(id);
        Long views = publicEventService.getViewsForEvent(event);
        UserShortDto initiator = publicEventService.getEventInitiator(event);
        CategoryDto category = categoryApiClient.getCategory(event.getCategoryId());
        return EventMapper.toFullDto(event, confirmedRequests, views, initiator, category);
    }

    @GetMapping("/list")
    public List<EventShortDto> getEventsByIds(@RequestParam List<Long> ids) {
        log.info("GET /events/list?ids={}", ids);
        List<Event> events = publicEventService.getEventsByIds(ids);
        if (events.isEmpty()) return List.of();

        Map<Long, Long> confirmedMap = publicEventService.getConfirmedRequestsCounts(ids);
        Map<Long, Long> viewsMap = publicEventService.getViewsForEvents(events);
        Map<Long, UserShortDto> initiatorMap = publicEventService.getEventInitiators(events);

        List<Long> categoryIds = events.stream().map(Event::getCategoryId).distinct().toList();
        Map<Long, CategoryDto> categoryMap = categoryApiClient.getCategories(0, 1000).stream()
                .collect(Collectors.toMap(CategoryDto::getId, c -> c));

        return events.stream()
                .map(event -> {
                    Long confirmedRequests = confirmedMap.getOrDefault(event.getId(), 0L);
                    Long views = viewsMap.getOrDefault(event.getId(), 0L);
                    UserShortDto initiator = initiatorMap.get(event.getInitiatorId());
                    CategoryDto category = categoryMap.get(event.getCategoryId());
                    return EventMapper.toShortDto(event, confirmedRequests, views, initiator, category);
                })
                .collect(Collectors.toList());
    }
}