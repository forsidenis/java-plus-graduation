package ru.practicum.main.service.event.service.impl;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import ru.practicum.common.dto.*;
import ru.practicum.common.exception.ConflictException;
import ru.practicum.common.exception.NotFoundException;
import ru.practicum.common.exception.ConditionsNotMetException;
import ru.practicum.main.service.event.mapper.EventMapper;
import ru.practicum.main.service.event.model.Event;
import ru.practicum.main.service.event.repository.EventRepository;
import ru.practicum.main.service.event.service.EventService;
import ru.practicum.stat.client.StatsClient;
import ru.practicum.stat.dto.EndpointHitDto;
import ru.practicum.stat.dto.ViewStatsDto;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
    private final DiscoveryClient discoveryClient;
    private final RestTemplate restTemplate;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // Вспомогательный метод для получения URL сервиса через DiscoveryClient
    private String getServiceUrl(String serviceName) {
        List<ServiceInstance> instances = discoveryClient.getInstances(serviceName);
        if (instances == null || instances.isEmpty()) {
            throw new IllegalStateException("No instances found for service: " + serviceName);
        }
        ServiceInstance instance = instances.get(0);
        return instance.getUri().toString();
    }

    // === Методы для вызова других сервисов через RestTemplate ===

    private UserShortDto getUserFromService(Long userId) {
        try {
            String url = getServiceUrl("user-service") + "/internal/users/" + userId;
            ResponseEntity<UserShortDto> response = restTemplate.exchange(
                    url, HttpMethod.GET, null, new ParameterizedTypeReference<UserShortDto>() {}
            );
            return response.getBody();
        } catch (Exception e) {
            log.warn("Failed to get user from user-service: {}", e.getMessage());
            return UserShortDto.builder()
                    .id(userId)
                    .name("dummy_user_" + userId)
                    .build();
        }
    }

    private CategoryDto getCategoryFromService(Long catId) {
        try {
            String url = getServiceUrl("category-service") + "/internal/categories/" + catId;
            ResponseEntity<CategoryDto> response = restTemplate.exchange(
                    url, HttpMethod.GET, null, new ParameterizedTypeReference<CategoryDto>() {}
            );
            return response.getBody();
        } catch (Exception e) {
            log.warn("Failed to get category from category-service: {}", e.getMessage());
            return CategoryDto.builder()
                    .id(catId)
                    .name("dummy_category_" + catId)
                    .build();
        }
    }

    private Long countConfirmedRequestsFromService(Long eventId) {
        try {
            String url = getServiceUrl("request-service") + "/internal/requests/count/" + eventId;
            ResponseEntity<Long> response = restTemplate.exchange(
                    url, HttpMethod.GET, null, new ParameterizedTypeReference<Long>() {}
            );
            return response.getBody() != null ? response.getBody() : 0L;
        } catch (Exception e) {
            log.warn("Failed to get confirmed requests from request-service: {}", e.getMessage());
            return 0L;
        }
    }

    private Boolean existsRequestConfirmedFromService(Long eventId, Long userId) {
        try {
            String url = getServiceUrl("request-service") + "/internal/requests/exists/" + eventId + "/" + userId;
            ResponseEntity<Boolean> response = restTemplate.exchange(
                    url, HttpMethod.GET, null, new ParameterizedTypeReference<Boolean>() {}
            );
            return response.getBody() != null && response.getBody();
        } catch (Exception e) {
            log.warn("Failed to check request existence from request-service: {}", e.getMessage());
            return false;
        }
    }

    private List<ParticipationRequestDto> getRequestsByEventFromService(Long eventId) {
        try {
            String url = getServiceUrl("request-service") + "/internal/requests/event/" + eventId;
            ResponseEntity<List<ParticipationRequestDto>> response = restTemplate.exchange(
                    url, HttpMethod.GET, null, new ParameterizedTypeReference<List<ParticipationRequestDto>>() {}
            );
            return response.getBody() != null ? response.getBody() : List.of();
        } catch (Exception e) {
            log.warn("Failed to get requests by event from request-service: {}", e.getMessage());
            return List.of();
        }
    }

    private EventRequestStatusUpdateResult updateRequestsStatusFromService(Long eventId, EventRequestStatusUpdateRequest request) {
        try {
            String url = getServiceUrl("request-service") + "/internal/requests/event/" + eventId + "/status";
            ResponseEntity<EventRequestStatusUpdateResult> response = restTemplate.exchange(
                    url, HttpMethod.PATCH, null, new ParameterizedTypeReference<EventRequestStatusUpdateResult>() {}
            );
            return response.getBody() != null ? response.getBody() :
                    EventRequestStatusUpdateResult.builder()
                            .confirmedRequests(List.of())
                            .rejectedRequests(List.of())
                            .build();
        } catch (Exception e) {
            log.warn("Failed to update requests status in request-service: {}", e.getMessage());
            return EventRequestStatusUpdateResult.builder()
                    .confirmedRequests(List.of())
                    .rejectedRequests(List.of())
                    .build();
        }
    }

    // === Основные методы сервиса ===

    @Override
    @Transactional
    public EventFullDto createEvent(Long userId, NewEventDto dto) {
        log.info("createEvent: userId={}", userId);
        UserShortDto user = getUserFromService(userId);
        if (user == null) {
            throw new NotFoundException("User not found id=" + userId);
        }
        CategoryDto category = getCategoryFromService(dto.getCategory());
        if (category == null) {
            throw new NotFoundException("Category not found id=" + dto.getCategory());
        }
        if (dto.getEventDate().isBefore(LocalDateTime.now().plusHours(2))) {
            throw new ConditionsNotMetException("Event date must be at least 2 hours later");
        }
        Event event = EventMapper.toEvent(dto, dto.getCategory(), userId);
        event = eventRepository.save(event);
        return EventMapper.toFullDto(event, category, user, 0L, 0L);
    }

    @Override
    public List<EventShortDto> getUserEvents(Long userId, int from, int size) {
        log.info("getUserEvents: userId={}", userId);
        UserShortDto user = getUserFromService(userId);
        if (user == null) {
            throw new NotFoundException("User not found");
        }
        Pageable pageable = PageRequest.of(from / size, size);
        List<Event> events = eventRepository.findAllByInitiatorId(userId, pageable);
        return enrichEventsWithStats(events, false);
    }

    @Override
    public EventFullDto getUserEventById(Long userId, Long eventId) {
        log.info("getUserEventById: userId={}, eventId={}", userId, eventId);
        Event event = findEventByIdAndInitiator(eventId, userId);
        UserShortDto user = getUserFromService(userId);
        if (user == null) {
            throw new NotFoundException("User not found");
        }
        CategoryDto category = getCategoryFromService(event.getCategoryId());
        Long confirmed = countConfirmedRequestsFromService(eventId);
        Long views = getViewsForEvent(eventId, event.getPublishedOn() != null ? event.getPublishedOn() : event.getCreatedOn());
        return EventMapper.toFullDto(event, category, user, confirmed, views);
    }

    @Override
    @Transactional
    public EventFullDto updateUserEvent(Long userId, Long eventId, UpdateEventUserRequest dto) {
        log.info("updateUserEvent: userId={}, eventId={}", userId, eventId);
        Event event = findEventByIdAndInitiator(eventId, userId);
        if (event.getState() != EventState.CANCELED && event.getState() != EventState.PENDING) {
            throw new ConflictException("Only pending or canceled events can be changed");
        }
        if (dto.getEventDate() != null && dto.getEventDate().isBefore(LocalDateTime.now().plusHours(2))) {
            throw new ConditionsNotMetException("Event date must be at least 2 hours later");
        }
        Long categoryId = null;
        if (dto.getCategory() != null) {
            CategoryDto category = getCategoryFromService(dto.getCategory());
            if (category == null) {
                throw new NotFoundException("Category not found");
            }
            categoryId = dto.getCategory();
        }
        EventMapper.updateEventFromUserRequest(event, dto, categoryId);
        if (dto.getStateAction() != null) {
            switch (dto.getStateAction()) {
                case "SEND_TO_REVIEW": event.setState(EventState.PENDING); break;
                case "CANCEL_REVIEW": event.setState(EventState.CANCELED); break;
                default: throw new IllegalArgumentException("Invalid action");
            }
        }
        event = eventRepository.save(event);
        UserShortDto user = getUserFromService(userId);
        CategoryDto category = getCategoryFromService(event.getCategoryId());
        Long confirmed = countConfirmedRequestsFromService(eventId);
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
            throw new IllegalArgumentException("rangeStart must be before rangeEnd");
        }
        if (rangeStart == null) {
            rangeStart = LocalDateTime.now();
        }
        Sort sortBy = (sort != null && sort.equals("VIEWS")) ? Sort.by(Sort.Direction.DESC, "id") : Sort.by(Sort.Direction.ASC, "eventDate");
        Pageable pageable = PageRequest.of(from / size, size, sortBy);
        List<Event> events = eventRepository.findPublicEvents(text, categories, paid, rangeStart, rangeEnd, pageable);
        if (Boolean.TRUE.equals(onlyAvailable)) {
            events = events.stream()
                    .filter(e -> {
                        if (e.getParticipantLimit() == 0) return true;
                        long confirmed = countConfirmedRequestsFromService(e.getId());
                        return confirmed < e.getParticipantLimit();
                    })
                    .collect(Collectors.toList());
        }
        List<EventShortDto> result = enrichEventsWithStats(events, true);
        if (sort != null && sort.equals("VIEWS")) {
            result.sort((a, b) -> Long.compare(b.getViews(), a.getViews()));
        }
        saveHit(request);
        return result;
    }

    @Override
    public EventFullDto getPublicEventById(Long eventId, HttpServletRequest request) {
        log.info("getPublicEventById: eventId={}", eventId);
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Event not found"));

        if (event.getState() != EventState.PUBLISHED) {
            throw new NotFoundException("Event is not published");
        }

        saveHit(request);
        UserShortDto user = getUserFromService(event.getInitiatorId());
        CategoryDto category = getCategoryFromService(event.getCategoryId());
        Long confirmed = countConfirmedRequestsFromService(eventId);
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
                .orElseThrow(() -> new NotFoundException("Event not found"));
        Long categoryId = null;
        if (dto.getCategory() != null) {
            CategoryDto category = getCategoryFromService(dto.getCategory());
            if (category == null) {
                throw new NotFoundException("Category not found");
            }
            categoryId = dto.getCategory();
        }
        EventMapper.updateEventFromAdminRequest(event, dto, categoryId);
        if (dto.getStateAction() != null) {
            switch (dto.getStateAction()) {
                case "PUBLISH_EVENT":
                    if (event.getState() != EventState.PENDING) throw new ConflictException("Event not pending");
                    if (event.getEventDate().isBefore(LocalDateTime.now().plusHours(1))) {
                        throw new ConditionsNotMetException("Event date must be at least 1 hour after publish");
                    }
                    event.setState(EventState.PUBLISHED);
                    event.setPublishedOn(LocalDateTime.now());
                    break;
                case "REJECT_EVENT":
                    if (event.getState() == EventState.PUBLISHED) throw new ConflictException("Cannot reject published");
                    event.setState(EventState.CANCELED);
                    break;
                default: throw new IllegalArgumentException("Invalid action");
            }
        }
        event = eventRepository.save(event);
        UserShortDto user = getUserFromService(event.getInitiatorId());
        CategoryDto category = getCategoryFromService(event.getCategoryId());
        Long confirmed = countConfirmedRequestsFromService(eventId);
        Long views = getViewsForEvent(eventId, event.getPublishedOn() != null ? event.getPublishedOn() : event.getCreatedOn());
        return EventMapper.toFullDto(event, category, user, confirmed, views);
    }

    @Override
    public EventFullDto getEventInternal(Long eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Event not found"));
        CategoryDto category = getCategoryFromService(event.getCategoryId());
        UserShortDto user = getUserFromService(event.getInitiatorId());
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

    // ===== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ =====

    private Event findEventByIdAndInitiator(Long eventId, Long userId) {
        return eventRepository.findById(eventId)
                .filter(e -> e.getInitiatorId().equals(userId))
                .orElseThrow(() -> new NotFoundException("Event not found or not owned"));
    }

    private Long getViewsForEvent(Long eventId, LocalDateTime start) {
        if (start == null) start = LocalDateTime.now().minusYears(10);
        try {
            List<ViewStatsDto> stats = statsClient.getStats(start, LocalDateTime.now(), List.of("/events/" + eventId), true);
            return stats.isEmpty() ? 0L : stats.get(0).getHits();
        } catch (Exception e) {
            log.warn("Failed to get views for event {}: {}", eventId, e.getMessage());
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
            log.warn("Failed to save hit: {}", e.getMessage());
        }
    }

    // ===== ВЫНЕСЕННЫЙ МЕТОД ДЛЯ ПОЛУЧЕНИЯ VIEWS =====
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
            log.warn("Failed to get views for events: {}", e.getMessage());
            return Map.of();
        }
    }

    private List<EventShortDto> enrichEventsWithStats(List<Event> events, boolean onlyPublished) {
        if (events.isEmpty()) return List.of();

        Map<Long, CategoryDto> categoryMap = events.stream()
                .map(Event::getCategoryId)
                .distinct()
                .collect(Collectors.toMap(id -> id, id -> {
                    try {
                        CategoryDto cat = getCategoryFromService(id);
                        return cat != null ? cat : CategoryDto.builder().id(id).build();
                    } catch (Exception e) {
                        return CategoryDto.builder().id(id).build();
                    }
                }));

        Map<Long, UserShortDto> userMap = events.stream()
                .map(Event::getInitiatorId)
                .distinct()
                .collect(Collectors.toMap(id -> id, id -> {
                    try {
                        UserShortDto user = getUserFromService(id);
                        return user != null ? user : UserShortDto.builder().id(id).build();
                    } catch (Exception e) {
                        return UserShortDto.builder().id(id).build();
                    }
                }));

        Map<Long, Long> confirmedMap = events.stream()
                .collect(Collectors.toMap(Event::getId, e -> countConfirmedRequestsFromService(e.getId())));

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
        if (events.isEmpty()) return List.of();

        Map<Long, CategoryDto> categoryMap = events.stream()
                .map(Event::getCategoryId)
                .distinct()
                .collect(Collectors.toMap(id -> id, id -> {
                    try {
                        CategoryDto cat = getCategoryFromService(id);
                        return cat != null ? cat : CategoryDto.builder().id(id).build();
                    } catch (Exception e) {
                        return CategoryDto.builder().id(id).build();
                    }
                }));

        Map<Long, UserShortDto> userMap = events.stream()
                .map(Event::getInitiatorId)
                .distinct()
                .collect(Collectors.toMap(id -> id, id -> {
                    try {
                        UserShortDto user = getUserFromService(id);
                        return user != null ? user : UserShortDto.builder().id(id).build();
                    } catch (Exception e) {
                        return UserShortDto.builder().id(id).build();
                    }
                }));

        Map<Long, Long> confirmedMap = events.stream()
                .collect(Collectors.toMap(Event::getId, e -> countConfirmedRequestsFromService(e.getId())));

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