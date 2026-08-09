package ru.practicum.event.service.impl;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import ru.practicum.RecommendationGrpcClient;
import ru.practicum.dto.eventDto.EventState;
import ru.practicum.dto.requestDto.RequestStatus;
import ru.practicum.dto.userDto.UserDto;
import ru.practicum.dto.userDto.UserShortDto;
import ru.practicum.event.model.Event;
import ru.practicum.event.repository.EventRepository;
import ru.practicum.event.service.PublicEventService;
import ru.practicum.exception.ConflictException;
import ru.practicum.exception.NotFoundException;
import ru.practicum.feign.RequestServiceFeign;
import ru.practicum.feign.UserServiceFeign;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class PublicEventServiceImpl implements PublicEventService {

    private final EventRepository eventRepository;
    private final RequestServiceFeign requestServiceFeign;
    private final UserServiceFeign userServiceFeign;
    private final RecommendationGrpcClient recommendationGrpcClient;

    @Override
    public List<Event> getPublicEvents(String text, List<Long> categories, Boolean paid,
                                       LocalDateTime rangeStart, LocalDateTime rangeEnd,
                                       Boolean onlyAvailable, String sort, int from, int size,
                                       HttpServletRequest request) {
        validateDateRange(rangeStart, rangeEnd);
        LocalDateTime start = rangeStart != null ? rangeStart : LocalDateTime.now();
        Pageable pageable = createPageable(sort, from, size);
        List<Event> events = eventRepository.findPublicEvents(text, categories, paid, start, rangeEnd, pageable);
        if (Boolean.TRUE.equals(onlyAvailable)) {
            events = filterOnlyAvailable(events);
        }
        return events;
    }

    @Override
    public Event getPublicEventById(Long eventId, HttpServletRequest request) {
        return eventRepository.findByIdAndState(eventId, EventState.PUBLISHED)
                .orElseThrow(() -> new NotFoundException("Событие с id=" + eventId + " не найдено"));
    }

    @Override
    public Event getPublicEventByIdWithoutHttp(Long eventId) {
        return findPublishedEventById(eventId);
    }

    @Override
    public Long getConfirmedRequestsCount(Long eventId) {
        return (long) requestServiceFeign.getAllByEventIdInAndStatus(1L, List.of(eventId), RequestStatus.CONFIRMED).size();
    }

    @Override
    public Double getRatingForEvent(Event event) {
        if (event == null) return 0.0;
        List<Long> eventIds = List.of(event.getId());
        try {
            var protoList = recommendationGrpcClient.getInteractionsCount(eventIds);
            return protoList.isEmpty() ? 0.0 : protoList.get(0).getScore();
        } catch (Exception e) {
            log.warn("Не удалось получить рейтинг для события {}: {}", event.getId(), e.getMessage());
            return 0.0;
        }
    }

    @Override
    public Map<Long, Double> getRatingsForEvents(List<Event> events) {
        if (events == null || events.isEmpty()) return Map.of();
        List<Long> eventIds = events.stream().map(Event::getId).collect(Collectors.toList());
        try {
            var protoList = recommendationGrpcClient.getInteractionsCount(eventIds);
            return protoList.stream()
                    .collect(Collectors.toMap(
                            proto -> proto.getEventId(),
                            proto -> proto.getScore()
                    ));
        } catch (Exception e) {
            log.warn("Не удалось получить рейтинги для событий: {}", e.getMessage());
            return Map.of();
        }
    }

    @Override
    public Map<Long, Long> getConfirmedRequestsCounts(List<Long> eventIds) {
        if (eventIds == null || eventIds.isEmpty()) return Map.of();
        return requestServiceFeign
                .getAllByEventIdInAndStatus(1L, eventIds, RequestStatus.CONFIRMED)
                .stream()
                .collect(Collectors.groupingBy(
                        request -> request.getEvent(),
                        Collectors.counting()
                ));
    }

    @Override
    public UserShortDto getEventInitiator(Event event) {
        if (event == null) return null;
        try {
            UserDto user = userServiceFeign.getUser(event.getInitiatorId());
            return new UserShortDto(user.getId(), user.getName());
        } catch (Exception e) {
            log.warn("Не удалось получить данные пользователя для события {}", event.getId());
            return null;
        }
    }

    @Override
    public Map<Long, UserShortDto> getEventInitiators(List<Event> events) {
        if (events == null || events.isEmpty()) return Map.of();
        List<Long> userIds = events.stream().map(Event::getInitiatorId).distinct().toList();
        try {
            List<UserDto> users = userServiceFeign.getAllUsersById(userIds);
            return users.stream()
                    .collect(Collectors.toMap(
                            UserDto::getId,
                            user -> new UserShortDto(user.getId(), user.getName())
                    ));
        } catch (Exception e) {
            log.warn("Не удалось получить данные пользователей для событий");
            return Map.of();
        }
    }

    @Override
    public List<Event> getEventsByIds(List<Long> ids) {
        return eventRepository.findAllById(ids);
    }

    private void validateDateRange(LocalDateTime start, LocalDateTime end) {
        if (start != null && end != null && start.isAfter(end)) {
            throw new IllegalArgumentException("Дата начала не может быть позже даты окончания");
        }
    }

    private Pageable createPageable(String sort, int from, int size) {
        Sort sortBy = (sort != null && sort.equals("VIEWS"))
                ? Sort.by(Sort.Direction.DESC, "id")
                : Sort.by(Sort.Direction.ASC, "eventDate");
        return PageRequest.of(from / size, size, sortBy);
    }

    private List<Event> filterOnlyAvailable(List<Event> events) {
        return events.stream().filter(this::isEventAvailable).collect(Collectors.toList());
    }

    private boolean isEventAvailable(Event event) {
        if (event.getParticipantLimit() == 0) return true;
        long confirmed = (long) requestServiceFeign.getAllByEventIdInAndStatus(1L, List.of(event.getId()), RequestStatus.CONFIRMED).size();
        return confirmed < event.getParticipantLimit();
    }

    private Event findPublishedEventById(Long eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Событие с id=" + eventId + " не найдено"));
        if (event.getState() != EventState.PUBLISHED) {
            throw new ConflictException("Событие с id=" + eventId + " не опубликовано");
        }
        return event;
    }
}