package ru.practicum.request.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import ru.practicum.common.dto.EventRequestStatusUpdateRequest;
import ru.practicum.common.dto.EventRequestStatusUpdateResult;
import ru.practicum.common.dto.ParticipationRequestDto;
import ru.practicum.common.dto.RequestStatus;
import ru.practicum.request.service.RequestService;

import java.util.List;

@RestController
@RequestMapping("/internal/requests")
@RequiredArgsConstructor
public class InternalRequestController {
    private final RequestService requestService;

    @GetMapping("/count/{eventId}")
    public Long countConfirmedRequests(@PathVariable Long eventId) {
        return requestService.countConfirmedRequests(eventId);
    }

    @GetMapping("/exists/{eventId}/{userId}")
    public boolean existsByEventAndUserAndStatusConfirmed(@PathVariable Long eventId,
                                                          @PathVariable Long userId) {
        return requestService.existsByEventAndUserAndStatusConfirmed(eventId, userId);
    }

    @GetMapping("/event/{eventId}")
    public List<ParticipationRequestDto> getRequestsByEvent(@PathVariable Long eventId) {
        return requestService.getRequestsByEvent(eventId);
    }

    @PatchMapping("/event/{eventId}/status")
    public EventRequestStatusUpdateResult updateRequestsStatus(@PathVariable Long eventId,
                                                               @RequestBody EventRequestStatusUpdateRequest request) {
        return requestService.updateRequestsStatusInternal(eventId, request);
    }

    @GetMapping("/all-with-status")
    public List<ParticipationRequestDto> getAllByEventIdInAndStatus(
            @RequestParam("eventIds") List<Long> eventIds,
            @RequestParam("status") RequestStatus status) {
        return requestService.getAllByEventIdInAndStatus(eventIds, status);
    }
}