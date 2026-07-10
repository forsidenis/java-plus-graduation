package ru.practicum.compilation.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import ru.practicum.common.dto.ParticipationRequestDto;
import ru.practicum.common.dto.RequestStatus;

import java.util.List;

@FeignClient(name = "request-service", fallback = RequestClientFallback.class)
public interface RequestClient {

    @GetMapping("/internal/requests/all-with-status")
    List<ParticipationRequestDto> getAllByEventIdInAndStatus(
            @RequestParam("eventIds") List<Long> eventIds,
            @RequestParam("status") RequestStatus status);
}