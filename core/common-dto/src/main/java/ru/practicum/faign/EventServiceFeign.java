package ru.practicum.faign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import ru.practicum.dto.eventDto.EventFullDto;
import ru.practicum.dto.eventDto.EventShortDto;

import java.util.List;

@FeignClient(name = "event-service", configuration = FeignConfig.class)
public interface EventServiceFeign {

    @GetMapping("/events/{id}/WithoutHttp")
    EventFullDto getEventByIdWithoutHttp(@PathVariable Long id);

    @GetMapping("/events/list")
    List<EventShortDto> getEventsByIds(@RequestParam("ids") List<Long> ids);
}