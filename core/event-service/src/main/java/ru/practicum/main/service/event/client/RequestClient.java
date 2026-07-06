package ru.practicum.main.service.event.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
import ru.practicum.common.dto.EventRequestStatusUpdateRequest;
import ru.practicum.common.dto.EventRequestStatusUpdateResult;
import ru.practicum.common.dto.ParticipationRequestDto;

import java.util.List;

@FeignClient(name = "request-service")
public interface RequestClient {

    @GetMapping("/internal/requests/count/{eventId}")
    Long countConfirmedRequests(@PathVariable("eventId") Long eventId);

    @GetMapping("/internal/requests/exists/{eventId}/{userId}")
    Boolean existsByEventAndUserAndStatusConfirmed(@PathVariable("eventId") Long eventId,
                                                   @PathVariable("userId") Long userId);

    @GetMapping("/internal/requests/event/{eventId}")
    List<ParticipationRequestDto> getRequestsByEvent(@PathVariable("eventId") Long eventId);

    @PatchMapping("/internal/requests/event/{eventId}/status")
    EventRequestStatusUpdateResult updateRequestsStatus(@PathVariable("eventId") Long eventId,
                                                        @RequestBody EventRequestStatusUpdateRequest request);
}