package ru.practicum.event.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import ru.practicum.RecommendationGrpcClient;
import ru.practicum.dto.eventDto.EventFullDto;
import ru.practicum.dto.eventDto.EventShortDto;
import ru.practicum.dto.requestDto.RequestStatus;
import ru.practicum.dto.userDto.UserShortDto;
import ru.practicum.event.mapper.EventMapper;
import ru.practicum.event.model.Event;
import ru.practicum.event.service.PublicEventService;
import ru.practicum.exception.ConditionsNotMetException;
import ru.practicum.feign.RequestServiceFeign;
import ru.practicum.ewm.stats.avro.ActionTypeAvro;
import stats.service.dashboard.RecommendedEventProto;

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
    private final RequestServiceFeign requestServiceFeign;
    private final RecommendationGrpcClient recommendationGrpcClient;

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

        if (events.isEmpty()) {
            return List.of();
        }

        Map<Long, Long> confirmedMap = publicEventService.getConfirmedRequestsCounts(
                events.stream().map(Event::getId).toList()
        );

        Map<Long, Double> ratingMap = publicEventService.getRatingsForEvents(events);

        Map<Long, UserShortDto> initiatorMap = publicEventService.getEventInitiators(events);

        return events.stream()
                .map(event -> {
                    Long confirmedRequests = confirmedMap.getOrDefault(event.getId(), 0L);
                    Double rating = ratingMap.getOrDefault(event.getId(), 0.0);
                    UserShortDto initiator = initiatorMap.get(event.getInitiatorId());

                    return EventMapper.toShortDto(event, confirmedRequests, rating, initiator);
                })
                .collect(Collectors.toList());
    }

    @GetMapping("/{id}")
    public EventFullDto getEventById(@PathVariable @Positive Long id,
                                     @RequestHeader(value = "X-EWM-USER-ID", required = false) Long userId,
                                     HttpServletRequest request) {
        log.info("GET /events/{} - получение события", id);

        Event event = publicEventService.getPublicEventById(id, request);

        if (userId != null) {
            try {
                recommendationGrpcClient.sendUserAction(userId, id, ActionTypeAvro.VIEW, System.currentTimeMillis());
            } catch (Exception e) {
                log.warn("Не удалось отправить просмотр для события {} пользователем {}: {}", id, userId, e.getMessage());
            }
        } else {
            log.debug("Просмотр не отправлен: userId отсутствует в заголовке");
        }

        Long confirmedRequests = publicEventService.getConfirmedRequestsCount(id);
        Double rating = publicEventService.getRatingForEvent(event);
        UserShortDto initiator = publicEventService.getEventInitiator(event);

        return EventMapper.toFullDto(event, confirmedRequests, rating, initiator);
    }

    @GetMapping("/{id}/WithoutHttp")
    public EventFullDto getEventByIdWithoutHttp(@PathVariable @Positive Long id) {
        log.info("GET /events/{} - получение события без учёта статистики", id);

        Event event = publicEventService.getPublicEventByIdWithoutHttp(id);

        Long confirmedRequests = publicEventService.getConfirmedRequestsCount(id);
        Double rating = publicEventService.getRatingForEvent(event);
        UserShortDto initiator = publicEventService.getEventInitiator(event);

        return EventMapper.toFullDto(event, confirmedRequests, rating, initiator);
    }

    @GetMapping("/recommendations")
    public List<EventShortDto> getRecommendations(@RequestHeader("X-EWM-USER-ID") Long userId,
                                                  @RequestParam(defaultValue = "10") int maxResults) {
        log.info("GET /events/recommendations для пользователя {}", userId);
        try {
            List<RecommendedEventProto> recommendations = recommendationGrpcClient.getRecommendationsForUser(userId, maxResults);
            if (recommendations.isEmpty()) {
                return List.of();
            }

            List<Long> eventIds = recommendations.stream()
                    .map(RecommendedEventProto::getEventId)
                    .collect(Collectors.toList());

            List<Event> events = publicEventService.getEventsByIds(eventIds);

            Map<Long, Double> ratingMap = recommendations.stream()
                    .collect(Collectors.toMap(
                            RecommendedEventProto::getEventId,
                            RecommendedEventProto::getScore
                    ));

            Map<Long, Long> confirmedMap = publicEventService.getConfirmedRequestsCounts(eventIds);

            Map<Long, UserShortDto> initiatorMap = publicEventService.getEventInitiators(events);

            return events.stream()
                    .map(event -> {
                        Long confirmed = confirmedMap.getOrDefault(event.getId(), 0L);
                        Double rating = ratingMap.getOrDefault(event.getId(), 0.0);
                        UserShortDto initiator = initiatorMap.get(event.getInitiatorId());
                        return EventMapper.toShortDto(event, confirmed, rating, initiator);
                    })
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Ошибка получения рекомендаций: {}", e.getMessage());
            return List.of();
        }
    }

    @PutMapping("/{eventId}/like")
    @ResponseStatus(HttpStatus.OK)
    public void likeEvent(@PathVariable Long eventId,
                          @RequestHeader("X-EWM-USER-ID") Long userId) {
        log.info("PUT /events/{}/like от пользователя {}", eventId, userId);

        boolean attended = requestServiceFeign.confirmUserRegisterOnEvent(userId, eventId, RequestStatus.CONFIRMED);
        if (!attended) {
            throw new ConditionsNotMetException("Пользователь не посещал данное мероприятие");
        }

        try {
            recommendationGrpcClient.sendUserAction(userId, eventId, ActionTypeAvro.LIKE, System.currentTimeMillis());
            log.info("Лайк на событие {} от пользователя {} отправлен", eventId, userId);
        } catch (Exception e) {
            log.error("Ошибка при отправке лайка: {}", e.getMessage());
            throw new RuntimeException("Не удалось отправить лайк", e);
        }
    }
}