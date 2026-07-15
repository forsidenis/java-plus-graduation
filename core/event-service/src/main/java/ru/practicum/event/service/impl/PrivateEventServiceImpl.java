package ru.practicum.event.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.dto.eventDto.EventState;
import ru.practicum.dto.eventDto.NewEventDto;
import ru.practicum.dto.eventDto.UpdateEventUserRequest;
import ru.practicum.dto.userDto.UserDto;
import ru.practicum.event.mapper.EventMapper;
import ru.practicum.event.mapper.LocationMapper;
import ru.practicum.event.model.Category;
import ru.practicum.event.model.Event;
import ru.practicum.event.repository.CategoryRepository;
import ru.practicum.event.repository.EventRepository;
import ru.practicum.event.service.PrivateEventService;
import ru.practicum.exception.ConditionsNotMetException;
import ru.practicum.exception.ConflictException;
import ru.practicum.exception.NotFoundException;
import ru.practicum.stat.client.StatsClient;
import ru.practicum.stat.dto.ViewStatsDto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class PrivateEventServiceImpl implements PrivateEventService {

    private final EventRepository eventRepository;
    private final CategoryRepository categoryRepository;
    private final StatsClient statsClient;

    @Override
    @Transactional
    public Event createEvent(Long userId, NewEventDto dto, UserDto user) {
        Category category = categoryRepository.findById(dto.getCategory())
                .orElseThrow(() -> new NotFoundException("Категория с id=" + dto.getCategory() + " не найдена"));
        validateEventDate(dto.getEventDate());
        Event event = EventMapper.toEvent(dto, category, user);
        return eventRepository.save(event);
    }

    @Override
    public List<Event> getUserEvents(Long userId, int from, int size) {
        Pageable pageable = PageRequest.of(from / size, size);
        return eventRepository.findAllByInitiatorId(userId, pageable);
    }

    @Override
    public Event getUserEventById(Long userId, Long eventId) {
        return eventRepository.findById(eventId)
                .filter(e -> e.getInitiatorId().equals(userId))
                .orElseThrow(() -> new NotFoundException("Событие не найдено или не принадлежит пользователю"));
    }

    @Override
    @Transactional
    public Event updateUserEvent(Long userId, Long eventId, UpdateEventUserRequest dto) {
        Event event = eventRepository.findById(eventId)
                .filter(e -> e.getInitiatorId().equals(userId))
                .orElseThrow(() -> new NotFoundException("Событие не найдено или не принадлежит пользователю"));
        if (event.getState() != EventState.CANCELED && event.getState() != EventState.PENDING) {
            throw new ConflictException("Изменить можно только отмененные или ожидающие события");
        }
        if (dto.getEventDate() != null) validateEventDate(dto.getEventDate());

        Category category = null;
        if (dto.getCategory() != null) {
            category = categoryRepository.findById(dto.getCategory())
                    .orElseThrow(() -> new NotFoundException("Категория не найдена"));
        }
        applyUpdateFields(event, dto, category);
        applyStateAction(event, dto.getStateAction());
        return eventRepository.save(event);
    }

    @Override
    public Long getViewsForEvent(Event event) {
        if (event == null) return 0L;
        LocalDateTime start = event.getPublishedOn() != null ? event.getPublishedOn() : event.getCreatedOn();
        if (start == null) start = LocalDateTime.now().minusYears(10);
        List<ViewStatsDto> stats = statsClient.getStats(start, LocalDateTime.now(), List.of("/events/" + event.getId()), true);
        return stats.isEmpty() ? 0L : stats.getFirst().getHits();
    }

    @Override
    public Map<Long, Long> getViewsForEvents(List<Event> events) {
        if (events == null || events.isEmpty()) return Map.of();
        LocalDateTime earliest = events.stream()
                .map(e -> e.getPublishedOn() != null ? e.getPublishedOn() : e.getCreatedOn())
                .min(LocalDateTime::compareTo)
                .orElse(LocalDateTime.now().minusYears(10));
        List<String> uris = events.stream().map(e -> "/events/" + e.getId()).toList();
        List<ViewStatsDto> stats = statsClient.getStats(earliest, LocalDateTime.now(), uris, false);
        return stats.stream()
                .collect(Collectors.toMap(
                        v -> Long.parseLong(v.getUri().substring(v.getUri().lastIndexOf('/') + 1)),
                        ViewStatsDto::getHits,
                        (a, b) -> a
                ));
    }

    private void validateEventDate(LocalDateTime eventDate) {
        if (eventDate.isBefore(LocalDateTime.now().plusHours(2))) {
            throw new ConditionsNotMetException("Дата события должна быть не ранее чем через 2 часа от текущего момента");
        }
    }

    private void applyUpdateFields(Event event, UpdateEventUserRequest dto, Category category) {
        if (dto.getTitle() != null) event.setTitle(dto.getTitle());
        if (dto.getAnnotation() != null) event.setAnnotation(dto.getAnnotation());
        if (dto.getDescription() != null) event.setDescription(dto.getDescription());
        if (dto.getEventDate() != null) event.setEventDate(dto.getEventDate());
        if (category != null) event.setCategory(category);
        if (dto.getLocation() != null) event.setLocation(LocationMapper.toLocation(dto.getLocation()));
        if (dto.getPaid() != null) event.setPaid(dto.getPaid());
        if (dto.getParticipantLimit() != null) event.setParticipantLimit(dto.getParticipantLimit());
        if (dto.getRequestModeration() != null) event.setRequestModeration(dto.getRequestModeration());
    }

    private void applyStateAction(Event event, String stateAction) {
        if (stateAction == null) return;
        switch (stateAction) {
            case "SEND_TO_REVIEW":
                event.setState(EventState.PENDING);
                break;
            case "CANCEL_REVIEW":
                event.setState(EventState.CANCELED);
                break;
            default:
                throw new IllegalArgumentException("Некорректное действие: " + stateAction);
        }
    }
}