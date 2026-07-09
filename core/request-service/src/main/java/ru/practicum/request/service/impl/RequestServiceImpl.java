package ru.practicum.request.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
        UserShortDto user = userClient.getUser(Long.valueOf(userId));
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

        UserShortDto user = userClient.getUser(Long.valueOf(userId));
        if (user == null) {
            throw new NotFoundException("Пользователь с id=" + userId + " не найден");
        }

        EventFullDto event = eventClient.getEvent(Long.valueOf(eventId));
        if (event == null) {
            throw new NotFoundException("Событие с id=" + eventId + " не найдено");
        }

        // Если инициатор не загружен – загружаем через UserClient
        if (event.getInitiator() == null) {
            UserShortDto initiator = userClient.getUser(event.getInitiator().getId());
            if (initiator == null) {
                throw new NotFoundException("Инициатор события с id=" + eventId + " не найден");
            }
            event.setInitiator(initiator);
        }

        // Проверяем, что пользователь не является инициатором
        if (event.getInitiator().getId().equals(Long.valueOf(userId))) {
            throw new ConflictException("Инициатор не может создать запрос на участие в своём событии");
        }

        // Проверяем, что событие опубликовано
        if (event.getState() != EventState.PUBLISHED) {
            throw new ConflictException("Событие не опубликовано");
        }

        // Проверяем, нет ли уже заявки от этого пользователя
        requestRepository.findByEventIdAndRequesterId(eventId, userId)
                .ifPresent(r -> {
                    throw new ConflictException("Повторный запрос на участие в событии");
                });

        // Проверяем лимит участников
        if (event.getParticipantLimit() > 0) {
            long confirmed = requestRepository.countByEventIdAndStatus(eventId, RequestStatus.CONFIRMED);
            if (confirmed >= event.getParticipantLimit()) {
                throw new ConflictException("Достигнут лимит участников события");
            }
        }

        // Создаём заявку
        ParticipationRequest request = new ParticipationRequest();
        request.setCreated(LocalDateTime.now());
        request.setEventId(Long.valueOf(eventId));
        request.setRequesterId(Long.valueOf(userId));
        request.setStatus(RequestStatus.PENDING);

        // Если модерация отключена или лимит 0 – сразу подтверждаем
        if (!event.getRequestModeration() || event.getParticipantLimit() == 0) {
            request.setStatus(RequestStatus.CONFIRMED);
        }

        request = requestRepository.save(request);
        log.info("Запрос создан id={}", request.getId());
        return RequestMapper.toDto(request);
    }

    @Override
    @Transactional
    public ParticipationRequestDto cancelRequest(Integer userId, Integer requestId) {
        log.info("cancelRequest: userId={}, requestId={}", userId, requestId);
        ParticipationRequest request = requestRepository.findByIdAndRequesterId(requestId, userId)
                .orElseThrow(() -> new NotFoundException("Запрос не найден или не принадлежит пользователю"));

        // Запрещаем отмену уже подтверждённой заявки
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
        EventFullDto event = eventClient.getEvent(eventId);
        if (event == null) {
            throw new NotFoundException("Событие с id=" + eventId + " не найдено");
        }

        if (event.getInitiator() == null) {
            UserShortDto initiator = userClient.getUser(event.getInitiator().getId());
            if (initiator == null) {
                throw new NotFoundException("Инициатор события с id=" + eventId + " не найден");
            }
            event.setInitiator(initiator);
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
        EventFullDto event = eventClient.getEvent(eventId);
        if (event == null) {
            throw new NotFoundException("Событие с id=" + eventId + " не найдено");
        }

        if (event.getInitiator() == null) {
            UserShortDto initiator = userClient.getUser(event.getInitiator().getId());
            if (initiator == null) {
                throw new NotFoundException("Инициатор события с id=" + eventId + " не найден");
            }
            event.setInitiator(initiator);
        }

        if (!event.getInitiator().getId().equals(userId)) {
            throw new ConditionsNotMetException("Пользователь не является инициатором события");
        }
        return updateRequestsInternal(eventId, updateRequest, event);
    }

    // === Внутренние методы ===

    @Override
    public Long countConfirmedRequests(Long eventId) {
        return requestRepository.countByEventIdAndStatus(eventId.intValue(), RequestStatus.CONFIRMED);
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
        EventFullDto event = eventClient.getEvent(eventId);
        if (event == null) {
            throw new NotFoundException("Событие с id=" + eventId + " не найдено");
        }
        return updateRequestsInternal(eventId, request, event);
    }

    // Общая логика обновления статусов
    private EventRequestStatusUpdateResult updateRequestsInternal(Long eventId,
                                                                  EventRequestStatusUpdateRequest updateRequest,
                                                                  EventFullDto event) {
        List<Integer> requestIds = updateRequest.getRequestIds().stream()
                .map(Long::intValue).collect(Collectors.toList());
        List<ParticipationRequest> requests = requestRepository.findAllByIdIn(requestIds);
        for (ParticipationRequest req : requests) {
            if (!req.getEventId().equals(eventId)) {
                throw new ConditionsNotMetException("Запрос не принадлежит этому событию");
            }
        }
        RequestStatus newStatus = updateRequest.getStatus();
        List<ParticipationRequest> confirmed = new ArrayList<>();
        List<ParticipationRequest> rejected = new ArrayList<>();

        if (newStatus == RequestStatus.CONFIRMED) {
            long confirmedCount = requestRepository.countByEventIdAndStatus(eventId.intValue(), RequestStatus.CONFIRMED);
            long limit = event.getParticipantLimit();
            for (ParticipationRequest req : requests) {
                if (req.getStatus() != RequestStatus.PENDING) {
                    throw new ConditionsNotMetException("Запрос не в статусе PENDING");
                }
                if (limit == 0 || confirmedCount < limit) {
                    req.setStatus(RequestStatus.CONFIRMED);
                    confirmed.add(req);
                    confirmedCount++;
                } else {
                    req.setStatus(RequestStatus.REJECTED);
                    rejected.add(req);
                    throw new ConflictException("Достигнут лимит участников");
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