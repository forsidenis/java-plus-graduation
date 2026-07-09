package ru.practicum.main.service.event.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.practicum.common.dto.EventRequestStatusUpdateRequest;
import ru.practicum.common.dto.EventRequestStatusUpdateResult;
import ru.practicum.common.dto.ParticipationRequestDto;

import java.util.Collections;
import java.util.List;

@Component
@Slf4j
public class RequestClientFallback implements RequestClient {
    @Override
    public Long countConfirmedRequests(Long eventId) {
        log.warn("RequestClient fallback: request-service unavailable, returning 0 for eventId={}", eventId);
        return 0L;
    }

    @Override
    public Boolean existsByEventAndUserAndStatusConfirmed(Long eventId, Long userId) {
        log.warn("RequestClient fallback: request-service unavailable, returning false for eventId={}, userId={}", eventId, userId);
        return false;
    }

    @Override
    public List<ParticipationRequestDto> getRequestsByEvent(Long eventId) {
        log.warn("RequestClient fallback: request-service unavailable, returning empty list for eventId={}", eventId);
        return Collections.emptyList();
    }

    @Override
    public EventRequestStatusUpdateResult updateRequestsStatus(Long eventId, EventRequestStatusUpdateRequest request) {
        log.warn("RequestClient fallback: request-service unavailable, returning empty result for eventId={}", eventId);
        return EventRequestStatusUpdateResult.builder()
                .confirmedRequests(Collections.emptyList())
                .rejectedRequests(Collections.emptyList())
                .build();
    }
}