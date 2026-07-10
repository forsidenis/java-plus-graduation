package ru.practicum.main.service.event.service.impl;

import feign.FeignException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.common.dto.*;
import ru.practicum.common.exception.ConflictException;
import ru.practicum.common.exception.NotFoundException;
import ru.practicum.main.service.event.client.CategoryClient;
import ru.practicum.main.service.event.client.RequestClient;
import ru.practicum.main.service.event.client.UserClient;
import ru.practicum.main.service.event.mapper.EventMapper;
import ru.practicum.main.service.event.model.Event;
import ru.practicum.main.service.event.repository.EventRepository;
import ru.practicum.main.service.event.service.EventService;
import ru.practicum.stat.client.StatsClient;
import ru.practicum.stat.dto.EndpointHitDto;
import ru.practicum.stat.dto.ViewStatsDto;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class EventServiceImpl implements EventService {

    private final EventRepository eventRepository;
    private final StatsClient statsClient;
    private final UserClient userClient;
    private final CategoryClient categoryClient;
    private final RequestClient requestClient;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // ----- Основные публичные методы -----

    @Override
    @Transactional
    public EventFullDto createEvent(Long userId, NewEventDto dto) {
        log.info("createEvent: userId={}", userId);
        // 1. Проверяем дату ДО вызовов внешних сервисов
        if (dto.getEventDate().isBefore(LocalDateTime.now().plusHours(2))) {
            throw new IllegalArgumentException("Дата события должна быть не ранее чем через 2 часа от текущего момента");
        }

        // 2. Получаем пользователя (fallback вернёт заглушку, но если его нет – будет исключение)
        UserShortDto user = userClient.getUser(userId);
        if (user == null || user.getId() == null) {
            throw new NotFoundException("Пользователь с id=" + userId + " не найден");
        }

        // 3. Получаем категорию (fallback вернёт заглушку, но если её нет – исключение)
        CategoryDto category = categoryClient.getCategory(dto.getCategory());
        if (category == null || category.getId() == null) {
            throw new NotFoundException("Категория с id=" + dto.getCategory() + " не найдена");
        }

        Event event = EventMapper.toEvent(dto, dto.getCategory(), userId);
        event = eventRepository.save(event);
        return EventMapper.toFullDto(event, category, user, 0L, 0L);
    }

    @Override
    public List<EventShortDto> getUserEvents(Long userId, int from, int size) {
        log.info("getUserEvents: userId={}", userId);
        // Проверяем существование пользователя (fallback вернёт заглушку, но если его нет – исключение)
        UserShortDto user = userClient.getUser(userId);
        if (user == null || user.getId() == null) {
            throw new NotFoundException("Пользователь с id=" + userId + " не найден");
        }

        Pageable pageable = PageRequest.of(from / size, size);
        List<Event> events = eventRepository.findAllByInitiatorId(userId, pageable);
        return enrichEventsWithStats(events, false);
    }

    @Override
    public EventFullDto getUserEventById(Long userId, Long eventId) {
        log.info("getUserEventById: userId={}, eventId={}", userId, eventId);
        Event event = findEventByIdAndInitiator(eventId, userId);
        UserShortDto user = userClient.getUser(userId);
        CategoryDto category = categoryClient.getCategory(event.getCategoryId());
        Long confirmed = requestClient.countConfirmedRequests(eventId);
        Long views = getViewsForEvent(eventId, event.getPublishedOn() != null ? event.getPublishedOn() : event.getCreatedOn());
        return EventMapper.toFullDto(event, category, user, confirmed, views);
    }

    @Override
    @Transactional
    public EventFullDto updateUserEvent(Long userId, Long eventId, UpdateEventUserRequest dto) {
        log.info("updateUserEvent: userId={}, eventId={}", userId, eventId);
        Event event = findEventByIdAndInitiator(eventId, userId);

        // Проверка даты
        if (dto.getEventDate() != null && dto.getEventDate().isBefore(LocalDateTime.now().plusHours(2))) {
            throw new IllegalArgumentException("Дата события должна быть не ранее чем через 2 часа от текущего момента");
        }

        if (event.getState() != EventState.CANCELED && event.getState() != EventState.PENDING) {
            throw new ConflictException("Изменять можно только события в статусе PENDING или CANCELED");
        }

        Long categoryId = null;
        if (dto.getCategory() != null) {
            CategoryDto category = categoryClient.getCategory(dto.getCategory());
            if (category == null || category.getId() == null) {
                throw new NotFoundException("Категория с id=" + dto.getCategory() + " не найдена");
            }
            categoryId = dto.getCategory();
        }

        EventMapper.updateEventFromUserRequest(event, dto, categoryId);

        if (dto.getStateAction() != null) {
            switch (dto.getStateAction()) {
                case "SEND_TO_REVIEW":
                    event.setState(EventState.PENDING);
                    break;
                case "CANCEL_REVIEW":
                    event.setState(EventState.CANCELED);
                    break;
                default:
                    throw new IllegalArgumentException("Некорректное действие: " + dto.getStateAction());
            }
        }

        event = eventRepository.save(event);
        UserShortDto user = userClient.getUser(userId);
        CategoryDto category = categoryClient.getCategory(event.getCategoryId());
        Long confirmed = requestClient.countConfirmedRequests(eventId);
        Long views = getViewsForEvent(eventId, event.getPublishedOn() != null ? event.getPublishedOn() : event.getCreatedOn());
        return EventMapper.toFullDto(event, category, user, confirmed, views);
    }

    @Override
    public List<EventShortDto> getPublicEvents(String text, List<Long> categories, Boolean paid,
                                               LocalDateTime rangeStart, LocalDateTime rangeEnd,
                                               Boolean onlyAvailable, String sort, int from, int size,
                                               HttpServletRequest request) {
        log.info("getPublicEvents");
        if (rangeStart != null && rangeEnd != null && rangeStart.isAfter(rangeEnd)) {
            throw new IllegalArgumentException("Дата начала не может быть позже даты окончания");
        }
        if (rangeStart == null) {
            rangeStart = LocalDateTime.now();
        }

        Pageable pageable = PageRequest.of(from / size, size);
        List<Event> events = eventRepository.findPublicEvents(text, categories, paid, rangeStart, rangeEnd, pageable);

        if (Boolean.TRUE.equals(onlyAvailable)) {
            events = events.stream()
                    .filter(e -> {
                        if (e.getParticipantLimit() == 0) return true;
                        long confirmed = requestClient.countConfirmedRequests(e.getId());
                        return confirmed < e.getParticipantLimit();
                    })
                    .collect(Collectors.toList());
        }

        List<EventShortDto> result = enrichEventsWithStats(events, true);

        if (sort != null && sort.equalsIgnoreCase("VIEWS")) {
            result.sort((a, b) -> Long.compare(b.getViews(), a.getViews()));
        } else {
            result.sort((a, b) -> a.getEventDate().compareTo(b.getEventDate()));
        }

        saveHit(request);
        return result;
    }

    @Override
    public EventFullDto getPublicEventById(Long eventId, HttpServletRequest request) {
        log.info("getPublicEventById: eventId={}", eventId);
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Событие с id=" + eventId + " не найдено"));

        if (event.getState() != EventState.PUBLISHED) {
            throw new NotFoundException("Событие не опубликовано");
        }

        saveHit(request);

        UserShortDto user = userClient.getUser(event.getInitiatorId());
        CategoryDto category = categoryClient.getCategory(event.getCategoryId());
        Long confirmed = requestClient.countConfirmedRequests(eventId);
        Long views = getViewsForEvent(eventId, event.getPublishedOn() != null ? event.getPublishedOn() : event.getCreatedOn());
        return EventMapper.toFullDto(event, category, user, confirmed, views);
    }

    @Override
    public List<EventFullDto> getAdminEvents(List<Long> users, List<EventState> states, List<Long> categories,
                                             LocalDateTime rangeStart, LocalDateTime rangeEnd, int from, int size) {
        log.info("getAdminEvents");
        Pageable pageable = PageRequest.of(from / size, size);
        List<Event> events = eventRepository.findAdminEvents(users, states, categories, rangeStart, rangeEnd, pageable);
        return enrichEventsFullWithStats(events);
    }

    @Override
    @Transactional
    public EventFullDto updateAdminEvent(Long eventId, UpdateEventAdminRequest dto) {
        log.info("updateAdminEvent: eventId={}", eventId);
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Событие с id=" + eventId + " не найдено"));

        // Проверка даты (если передана)
        if (dto.getEventDate() != null && dto.getEventDate().isBefore(LocalDateTime.now().plusHours(1))) {
            throw new IllegalArgumentException("Дата события должна быть не ранее чем через 1 час после публикации");
        }

        Long categoryId = null;
        if (dto.getCategory() != null) {
            CategoryDto category = categoryClient.getCategory(dto.getCategory());
            if (category == null || category.getId() == null) {
                throw new NotFoundException("Категория с id=" + dto.getCategory() + " не найдена");
            }
            categoryId = dto.getCategory();
        }

        EventMapper.updateEventFromAdminRequest(event, dto, categoryId);

        if (dto.getStateAction() != null) {
            switch (dto.getStateAction()) {
                case "PUBLISH_EVENT":
                    if (event.getState() != EventState.PENDING) {
                        throw new ConflictException("Событие не в статусе PENDING");
                    }
                    if (event.getEventDate().isBefore(LocalDateTime.now().plusHours(1))) {
                        throw new IllegalArgumentException("Дата события должна быть не ранее чем через 1 час после публикации");
                    }
                    event.setState(EventState.PUBLISHED);
                    event.setPublishedOn(LocalDateTime.now());
                    break;
                case "REJECT_EVENT":
                    if (event.getState() == EventState.PUBLISHED) {
                        throw new ConflictException("Нельзя отклонить опубликованное событие");
                    }
                    event.setState(EventState.CANCELED);
                    break;
                default:
                    throw new IllegalArgumentException("Некорректное действие: " + dto.getStateAction());
            }
        }

        event = eventRepository.save(event);
        UserShortDto user = userClient.getUser(event.getInitiatorId());
        CategoryDto category = categoryClient.getCategory(event.getCategoryId());
        Long confirmed = requestClient.countConfirmedRequests(eventId);
        Long views = getViewsForEvent(eventId, event.getPublishedOn() != null ? event.getPublishedOn() : event.getCreatedOn());
        return EventMapper.toFullDto(event, category, user, confirmed, views);
    }

    // ----- Внутренние методы для других сервисов -----

    @Override
    public EventFullDto getEventInternal(Long eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Событие не найдено"));
        CategoryDto category = categoryClient.getCategory(event.getCategoryId());
        UserShortDto user = userClient.getUser(event.getInitiatorId());
        return EventMapper.toFullDto(event, category, user, 0L, 0L);
    }

    @Override
    public List<EventShortDto> getEventsByIdsInternal(List<Long> ids) {
        List<Event> events = eventRepository.findAllById(ids);
        return events.stream()
                .map(EventMapper::toShortDto)
                .collect(Collectors.toList());
    }

    @Override
    public boolean isEventPublished(Long eventId) {
        return eventRepository.findById(eventId)
                .map(e -> e.getState() == EventState.PUBLISHED)
                .orElse(false);
    }

    @Override
    public List<ParticipationRequestDto> getRequestsByEvent(Long eventId) {
        return requestClient.getRequestsByEvent(eventId);
    }

    @Override
    public EventRequestStatusUpdateResult updateRequestsStatus(Long eventId, EventRequestStatusUpdateRequest request) {
        return requestClient.updateRequestsStatus(eventId, request);
    }

    // ----- Вспомогательные методы -----

    private Event findEventByIdAndInitiator(Long eventId, Long userId) {
        return eventRepository.findById(eventId)
                .filter(e -> e.getInitiatorId().equals(userId))
                .orElseThrow(() -> new NotFoundException("Событие не найдено или не принадлежит пользователю"));
    }

    private Long getViewsForEvent(Long eventId, LocalDateTime start) {
        try {
            if (start == null) {
                start = LocalDateTime.now().minusYears(10);
            }
            List<ViewStatsDto> stats = statsClient.getStats(start, LocalDateTime.now(), List.of("/events/" + eventId), true);
            return stats.isEmpty() ? 0L : stats.get(0).getHits();
        } catch (Exception e) {
            log.warn("Не удалось получить просмотры для события {}: {}", eventId, e.getMessage());
            return 0L;
        }
    }

    private void saveHit(HttpServletRequest request) {
        try {
            statsClient.hit(EndpointHitDto.builder()
                    .app("ewm-main-service")
                    .uri(request.getRequestURI())
                    .ip(request.getRemoteAddr())
                    .timestamp(LocalDateTime.now())
                    .build());
        } catch (Exception e) {
            log.warn("Не удалось сохранить обращение: {}", e.getMessage());
        }
    }

    private Map<Long, Long> getViewsMap(List<Event> events, LocalDateTime earliest) {
        try {
            List<String> uris = events.stream()
                    .map(e -> "/events/" + e.getId())
                    .collect(Collectors.toList());
            List<ViewStatsDto> stats = statsClient.getStats(earliest, LocalDateTime.now(), uris, false);
            return stats.stream()
                    .collect(Collectors.toMap(
                            v -> Long.parseLong(v.getUri().substring(v.getUri().lastIndexOf('/') + 1)),
                            ViewStatsDto::getHits,
                            (a, b) -> a
                    ));
        } catch (Exception e) {
            log.warn("Не удалось получить просмотры для событий: {}", e.getMessage());
            return Map.of();
        }
    }

    private List<EventShortDto> enrichEventsWithStats(List<Event> events, boolean onlyPublished) {
        if (events.isEmpty()) return Collections.emptyList();

        // Получаем категории и пользователей через fallback-клиенты (они вернут заглушки при ошибках)
        Map<Long, CategoryDto> categoryMap = events.stream()
                .map(Event::getCategoryId)
                .distinct()
                .collect(Collectors.toMap(
                        id -> id,
                        id -> {
                            try {
                                return categoryClient.getCategory(id);
                            } catch (Exception e) {
                                return CategoryDto.builder().id(id).name("dummy").build();
                            }
                        }
                ));

        Map<Long, UserShortDto> userMap = events.stream()
                .map(Event::getInitiatorId)
                .distinct()
                .collect(Collectors.toMap(
                        id -> id,
                        id -> {
                            try {
                                return userClient.getUser(id);
                            } catch (Exception e) {
                                return UserShortDto.builder().id(id).name("dummy").build();
                            }
                        }
                ));

        Map<Long, Long> confirmedMap = events.stream()
                .collect(Collectors.toMap(Event::getId, e -> {
                    try {
                        return requestClient.countConfirmedRequests(e.getId());
                    } catch (Exception e1) {
                        return 0L;
                    }
                }));

        LocalDateTime earliest = events.stream()
                .map(e -> e.getPublishedOn() != null ? e.getPublishedOn() : e.getCreatedOn())
                .min(LocalDateTime::compareTo)
                .orElse(LocalDateTime.now().minusYears(10));

        final Map<Long, Long> viewsMap = getViewsMap(events, earliest);

        return events.stream()
                .map(e -> EventMapper.toShortDto(e,
                        categoryMap.get(e.getCategoryId()),
                        userMap.get(e.getInitiatorId()),
                        confirmedMap.getOrDefault(e.getId(), 0L),
                        viewsMap.getOrDefault(e.getId(), 0L)))
                .collect(Collectors.toList());
    }

    private List<EventFullDto> enrichEventsFullWithStats(List<Event> events) {
        if (events.isEmpty()) return Collections.emptyList();

        Map<Long, CategoryDto> categoryMap = events.stream()
                .map(Event::getCategoryId)
                .distinct()
                .collect(Collectors.toMap(
                        id -> id,
                        id -> {
                            try {
                                return categoryClient.getCategory(id);
                            } catch (Exception e) {
                                return CategoryDto.builder().id(id).name("dummy").build();
                            }
                        }
                ));

        Map<Long, UserShortDto> userMap = events.stream()
                .map(Event::getInitiatorId)
                .distinct()
                .collect(Collectors.toMap(
                        id -> id,
                        id -> {
                            try {
                                return userClient.getUser(id);
                            } catch (Exception e) {
                                return UserShortDto.builder().id(id).name("dummy").build();
                            }
                        }
                ));

        Map<Long, Long> confirmedMap = events.stream()
                .collect(Collectors.toMap(Event::getId, e -> {
                    try {
                        return requestClient.countConfirmedRequests(e.getId());
                    } catch (Exception e1) {
                        return 0L;
                    }
                }));

        LocalDateTime earliest = events.stream()
                .map(e -> e.getPublishedOn() != null ? e.getPublishedOn() : e.getCreatedOn())
                .min(LocalDateTime::compareTo)
                .orElse(LocalDateTime.now().minusYears(10));

        final Map<Long, Long> viewsMap = getViewsMap(events, earliest);

        return events.stream()
                .map(e -> EventMapper.toFullDto(e,
                        categoryMap.get(e.getCategoryId()),
                        userMap.get(e.getInitiatorId()),
                        confirmedMap.getOrDefault(e.getId(), 0L),
                        viewsMap.getOrDefault(e.getId(), 0L)))
                .collect(Collectors.toList());
    }
}