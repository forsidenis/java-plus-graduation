package ru.practicum.request.controller;

import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import ru.practicum.common.dto.EventRequestStatusUpdateRequest;
import ru.practicum.common.dto.EventRequestStatusUpdateResult;
import ru.practicum.common.dto.ParticipationRequestDto;
import ru.practicum.common.dto.RequestStatus;
import ru.practicum.request.service.RequestService;

import java.util.List;

@RestController
@RequestMapping("/users/{userId}")
@RequiredArgsConstructor
@Validated
@Slf4j
public class PrivateRequestController {

    private final RequestService requestService;

    @GetMapping("/requests")
    public List<ParticipationRequestDto> getUserRequests(@PathVariable @Positive Long userId) {
        log.info("GET /users/{}/requests", userId);
        return requestService.getUserRequests(userId);
    }

    @PostMapping("/requests")
    @ResponseStatus(HttpStatus.CREATED)
    public ParticipationRequestDto createRequest(@PathVariable @Positive Long userId,
                                                 @RequestParam @Positive Long eventId) {
        log.info("POST /users/{}/requests?eventId={}", userId, eventId);
        return requestService.createRequest(userId, eventId);
    }

    @PatchMapping("/requests/{requestId}/cancel")
    public ParticipationRequestDto cancelRequest(@PathVariable @Positive Long userId,
                                                 @PathVariable @Positive Long requestId) {
        log.info("PATCH /users/{}/requests/{}/cancel", userId, requestId);
        return requestService.cancelRequest(userId, requestId);
    }

    @GetMapping("/events/{eventId}/requests")
    public List<ParticipationRequestDto> getEventRequests(@PathVariable @Positive Long userId,
                                                          @PathVariable @Positive Long eventId) {
        log.info("GET /users/{}/events/{}/requests", userId, eventId);
        return requestService.getEventRequests(userId, eventId);
    }

    @PostMapping("/events/{eventId}/requests")
    public EventRequestStatusUpdateResult updateEventRequestsStatus(@PathVariable @Positive Long userId,
                                                                    @PathVariable @Positive Long eventId,
                                                                    @RequestBody EventRequestStatusUpdateRequest updateRequest) {
        log.info("POST /users/{}/events/{}/requests с телом: {}", userId, eventId, updateRequest);
        return requestService.updateEventRequestsStatus(userId, eventId, updateRequest);
    }

    @GetMapping("/requests/allWithStatus/list")
    public List<ParticipationRequestDto> getAllByEventIdInAndStatus(
            @PathVariable Long userId,
            @RequestParam("eventIds") List<Long> eventIds,
            @RequestParam("status") RequestStatus status) {
        log.info("GET /users/{}/requests/allWithStatus/list?eventIds={}&status={}", userId, eventIds, status);
        return requestService.getAllByEventIdInAndStatus(eventIds, status);
    }
}