package ru.practicum.request.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import ru.practicum.common.dto.*;
import ru.practicum.common.exception.ConflictException;
import ru.practicum.common.exception.NotFoundException;
import ru.practicum.common.exception.ConditionsNotMetException;
import ru.practicum.request.mapper.RequestMapper;
import ru.practicum.request.model.ParticipationRequest;
import ru.practicum.request.repository.RequestRepository;
import ru.practicum.request.service.RequestService;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class RequestServiceImpl implements RequestService {

    private final RequestRepository requestRepository;
    private final DiscoveryClient discoveryClient;
    private final RestTemplate restTemplate;

    private String getServiceUrl(String serviceName) {
        List<ServiceInstance> instances = discoveryClient.getInstances(serviceName);
        if (instances == null || instances.isEmpty()) {
            throw new IllegalStateException("Нет доступных экземпляров сервиса: " + serviceName);
        }
        return instances.get(0).getUri().toString();
    }

    private UserShortDto getUserFromService(Long userId) {
        try {
            String url = getServiceUrl("user-service") + "/internal/users/" + userId;
            ResponseEntity<UserShortDto> response = restTemplate.exchange(
                    url, HttpMethod.GET, null, new ParameterizedTypeReference<UserShortDto>() {
                    }
            );
            return response.getBody();
        } catch (Exception e) {
            log.warn("Не удалось получить пользователя из user-service: {}", e.getMessage());
            return UserShortDto.builder().id(userId).name("dummy_user_" + userId).build();
        }
    }

    private EventFullDto getEventFromService(Long eventId) {
        try {
            String url = getServiceUrl("event-service") + "/internal/events/" + eventId;
            ResponseEntity<EventFullDto> response = restTemplate.exchange(
                    url, HttpMethod.GET, null, new ParameterizedTypeReference<EventFullDto>() {
                    }
            );
            EventFullDto event = response.getBody();
            if (event != null && event.getInitiator() == null) {
                UserShortDto dummy = getUserFromService(event.getInitiator().getId());
                event.setInitiator(dummy != null ? dummy : UserShortDto.builder()
                        .id(1L)
                        .name("dummy_initiator")
                        .build());
            }
            return event;
        } catch (Exception e) {
            log.warn("Не удалось получить событие из event-service: {}", e.getMessage());
            return EventFullDto.builder()
                    .id(eventId)
                    .state(EventState.PUBLISHED)
                    .participantLimit(0)
                    .requestModeration(true)
                    .initiator(UserShortDto.builder().id(1L).name("dummy_initiator").build())
                    .build();
        }
    }

    @Override
    public List<ParticipationRequestDto> getUserRequests(Integer userId) {
        log.info("getUserRequests: userId={}", userId);
        UserShortDto user = getUserFromService(Long.valueOf(userId));
        if (user == null) {
            throw new NotFoundException("Пользователь с id=" + userId + " не найден");
        }
        return requestRepository.findAllByRequesterId(userId).stream()
                .map(RequestMapper::toDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public ParticipationRequestDto createRequest(Integer userId, Integer eventId) {
        log.info("createRequest: userId={}, eventId={}", userId, eventId);

        UserShortDto user = getUserFromService(Long.valueOf(userId));
        if (user == null) {
            throw new NotFoundException("Пользователь с id=" + userId + " не найден");
        }

        EventFullDto event = getEventFromService(Long.valueOf(eventId));
        if (event == null) {
            throw new NotFoundException("Событие с id=" + eventId + " не найдено");
        }

        if (event.getInitiator() == null) {
            throw new IllegalStateException("Инициатор события не заполнен");
        }

        if (event.getInitiator().getId().equals(Long.valueOf(userId))) {
            throw new ConflictException("Инициатор не может подать заявку на своё событие");
        }

        if (event.getState() != EventState.PUBLISHED) {
            throw new ConflictException("Событие не опубликовано");
        }

        requestRepository.findByEventIdAndRequesterId(eventId, userId)
                .ifPresent(r -> {
                    throw new ConflictException("Повторная заявка не допускается");
                });

        int participantLimit = event.getParticipantLimit() != null ? event.getParticipantLimit() : 0;
        // Проверка лимита по подтверждённым заявкам
        if (participantLimit > 0) {
            long confirmed = requestRepository.countByEventIdAndStatus(Long.valueOf(eventId), RequestStatus.CONFIRMED);
            if (confirmed >= participantLimit) {
                throw new ConflictException("Достигнут лимит участников события");
            }
        }

        ParticipationRequest request = new ParticipationRequest();
        request.setCreated(LocalDateTime.now());
        request.setEventId(Long.valueOf(eventId));
        request.setRequesterId(Long.valueOf(userId));

        boolean moderation = event.getRequestModeration() != null ? event.getRequestModeration() : true;
        if (participantLimit == 0 || !moderation) {
            request.setStatus(RequestStatus.CONFIRMED);
        } else {
            request.setStatus(RequestStatus.PENDING);
        }

        request = requestRepository.save(request);
        log.info("Заявка создана с id={}, статус={}", request.getId(), request.getStatus());
        return RequestMapper.toDto(request);
    }

    @Override
    @Transactional
    public ParticipationRequestDto cancelRequest(Integer userId, Integer requestId) {
        log.info("cancelRequest: userId={}, requestId={}", userId, requestId);
        ParticipationRequest request = requestRepository.findByIdAndRequesterId(requestId, userId)
                .orElseThrow(() -> new NotFoundException("Заявка не найдена или не принадлежит пользователю"));

        if (request.getStatus() == RequestStatus.CONFIRMED) {
            throw new ConflictException("Нельзя отменить уже подтверждённую заявку");
        }

        request.setStatus(RequestStatus.CANCELED);
        request = requestRepository.save(request);
        return RequestMapper.toDto(request);
    }

    @Override
    public List<ParticipationRequestDto> getEventRequests(Long userId, Long eventId) {
        log.info("getEventRequests: userId={}, eventId={}", userId, eventId);
        EventFullDto event = getEventFromService(eventId);
        if (event == null) {
            throw new NotFoundException("Событие с id=" + eventId + " не найдено");
        }

        if (event.getInitiator() == null) {
            throw new IllegalStateException("Инициатор события не заполнен");
        }

        if (!event.getInitiator().getId().equals(userId)) {
            throw new ConditionsNotMetException("Пользователь не является инициатором события");
        }

        return requestRepository.findAllByEventId(eventId.intValue()).stream()
                .map(RequestMapper::toDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public EventRequestStatusUpdateResult updateEventRequestsStatus(Long userId, Long eventId,
                                                                    EventRequestStatusUpdateRequest updateRequest) {
        log.info("updateEventRequestsStatus: userId={}, eventId={}", userId, eventId);
        EventFullDto event = getEventFromService(eventId);
        if (event == null) {
            throw new NotFoundException("Событие с id=" + eventId + " не найдено");
        }

        if (event.getInitiator() == null) {
            throw new IllegalStateException("Инициатор события не заполнен");
        }

        if (!event.getInitiator().getId().equals(userId)) {
            throw new ConditionsNotMetException("Пользователь не является инициатором события");
        }
        return updateRequestsInternal(eventId, updateRequest, event);
    }

    // --- Внутренние методы ---

    @Override
    public Long countConfirmedRequests(Long eventId) {
        return requestRepository.countByEventIdAndStatus(eventId, RequestStatus.CONFIRMED);
    }

    @Override
    public boolean existsByEventAndUserAndStatusConfirmed(Long eventId, Long userId) {
        return requestRepository.existsByEventIdAndRequesterIdAndStatus(
                eventId.intValue(), userId.intValue(), RequestStatus.CONFIRMED);
    }

    @Override
    public List<ParticipationRequestDto> getRequestsByEvent(Long eventId) {
        return requestRepository.findAllByEventId(eventId.intValue()).stream()
                .map(RequestMapper::toDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public EventRequestStatusUpdateResult updateRequestsStatusInternal(Long eventId,
                                                                       EventRequestStatusUpdateRequest request) {
        EventFullDto event = getEventFromService(eventId);
        if (event == null) {
            throw new NotFoundException("Событие не найдено");
        }
        return updateRequestsInternal(eventId, request, event);
    }

    private EventRequestStatusUpdateResult updateRequestsInternal(Long eventId,
                                                                  EventRequestStatusUpdateRequest updateRequest,
                                                                  EventFullDto event) {
        List<Integer> requestIds = updateRequest.getRequestIds().stream()
                .map(Long::intValue).collect(Collectors.toList());
        List<ParticipationRequest> requests = requestRepository.findAllByIdIn(requestIds);
        for (ParticipationRequest req : requests) {
            if (!req.getEventId().equals(eventId)) {
                throw new ConditionsNotMetException("Заявка не принадлежит данному событию");
            }
        }
        RequestStatus newStatus = updateRequest.getStatus();
        List<ParticipationRequest> confirmed = new ArrayList<>();
        List<ParticipationRequest> rejected = new ArrayList<>();

        int participantLimit = event.getParticipantLimit() != null ? event.getParticipantLimit() : 0;

        if (newStatus == RequestStatus.CONFIRMED) {
            long confirmedCount = requestRepository.countByEventIdAndStatus(eventId, RequestStatus.CONFIRMED);
            for (ParticipationRequest req : requests) {
                if (req.getStatus() != RequestStatus.PENDING) {
                    throw new ConditionsNotMetException("Заявка не в статусе PENDING");
                }
                if (participantLimit == 0 || confirmedCount < participantLimit) {
                    req.setStatus(RequestStatus.CONFIRMED);
                    confirmed.add(req);
                    confirmedCount++;
                } else {
                    req.setStatus(RequestStatus.REJECTED);
                    rejected.add(req);
                    throw new ConflictException("Достигнут лимит участников события");
                }
            }
        } else if (newStatus == RequestStatus.REJECTED) {
            for (ParticipationRequest req : requests) {
                if (req.getStatus() == RequestStatus.CONFIRMED) {
                    throw new ConflictException("Нельзя отклонить уже подтверждённую заявку");
                }
                if (req.getStatus() != RequestStatus.REJECTED) {
                    req.setStatus(RequestStatus.REJECTED);
                    rejected.add(req);
                } else {
                    rejected.add(req);
                }
            }
        } else {
            throw new IllegalArgumentException("Некорректный статус");
        }
        requestRepository.saveAll(requests);
        return EventRequestStatusUpdateResult.builder()
                .confirmedRequests(confirmed.stream().map(RequestMapper::toDto).collect(Collectors.toList()))
                .rejectedRequests(rejected.stream().map(RequestMapper::toDto).collect(Collectors.toList()))
                .build();
    }
}