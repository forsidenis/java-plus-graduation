package ru.practicum.request.service;

import ru.practicum.common.dto.EventRequestStatusUpdateRequest;
import ru.practicum.common.dto.EventRequestStatusUpdateResult;
import ru.practicum.common.dto.ParticipationRequestDto;

import java.util.List;

public interface RequestService {
    List<ParticipationRequestDto> getUserRequests(Integer userId);

    ParticipationRequestDto createRequest(Integer userId, Integer eventId);

    ParticipationRequestDto cancelRequest(Integer userId, Integer requestId);

    List<ParticipationRequestDto> getEventRequests(Long userId, Long eventId);

    EventRequestStatusUpdateResult updateEventRequestsStatus(Long userId, Long eventId,
                                                             EventRequestStatusUpdateRequest updateRequest);

    // Внутренние методы для других сервисов
    Long countConfirmedRequests(Long eventId);

    boolean existsByEventAndUserAndStatusConfirmed(Long eventId, Long userId);

    List<ParticipationRequestDto> getRequestsByEvent(Long eventId);

    EventRequestStatusUpdateResult updateRequestsStatusInternal(Long eventId,
                                                                EventRequestStatusUpdateRequest request);
}