package ru.practicum.request.service.impl;

import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.common.dto.*;
import ru.practicum.common.exception.ConflictException;
import ru.practicum.common.exception.NotFoundException;
import ru.practicum.common.exception.ConditionsNotMetException;
import ru.practicum.request.client.EventClient;
import ru.practicum.request.client.UserClient;
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
    private final UserClient userClient;
    private final EventClient eventClient;

    @Override
    public List<ParticipationRequestDto> getUserRequests(Integer userId) {
        log.info("getUserRequests: userId={}", userId);
        try {
            userClient.getUser(Long.valueOf(userId));
        } catch (FeignException e) {
            throw new NotFoundException("Пользователь не найден или сервис недоступен");
        }
        return requestRepository.findAllByRequesterId(userId).stream()
                .map(RequestMapper::toDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public ParticipationRequestDto createRequest(Integer userId, Integer eventId) {
        log.info("createRequest: userId={}, eventId={}", userId, eventId);

        UserShortDto user;
        try {
            user = userClient.getUser(Long.valueOf(userId));
        } catch (FeignException e) {
            throw new NotFoundException("Пользователь не найден или сервис недоступен");
        }
        if (user == null || user.getId() == null) {
            throw new NotFoundException("Пользователь с id=" + userId + " не найден");
        }

        EventFullDto event;
        try {
            event = eventClient.getEvent(Long.valueOf(eventId));
        } catch (FeignException e) {
            throw new NotFoundException("Событие не найдено или сервис недоступен");
        }
        if (event == null || event.getId() == null) {
            throw new NotFoundException("Событие с id=" + eventId + " не найдено");
        }

        if (event.getInitiator() == null || event.getInitiator().getId() == null) {
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

        try {
            request = requestRepository.save(request);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("Повторная заявка не допускается");
        }
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
        EventFullDto event;
        try {
            event = eventClient.getEvent(eventId);
        } catch (FeignException e) {
            throw new NotFoundException("Событие не найдено или сервис недоступен");
        }
        if (event == null || event.getId() == null) {
            throw new NotFoundException("Событие с id=" + eventId + " не найдено");
        }
        if (event.getInitiator() == null || event.getInitiator().getId() == null) {
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
        EventFullDto event;
        try {
            event = eventClient.getEvent(eventId);
        } catch (FeignException e) {
            throw new NotFoundException("Событие не найдено или сервис недоступен");
        }
        if (event == null || event.getId() == null) {
            throw new NotFoundException("Событие с id=" + eventId + " не найдено");
        }
        if (event.getInitiator() == null || event.getInitiator().getId() == null) {
            throw new IllegalStateException("Инициатор события не заполнен");
        }
        if (!event.getInitiator().getId().equals(userId)) {
            throw new ConditionsNotMetException("Пользователь не является инициатором события");
        }
        return updateRequestsInternal(eventId, updateRequest, event);
    }

    // ----- Внутренние методы (для других сервисов) -----

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
        EventFullDto event;
        try {
            event = eventClient.getEvent(eventId);
        } catch (FeignException e) {
            throw new NotFoundException("Событие не найдено или сервис недоступен");
        }
        if (event == null || event.getId() == null) {
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