package ru.practicum.event.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import ru.practicum.dto.compilationDto.CompilationDto;
import ru.practicum.dto.compilationDto.NewCompilationDto;
import ru.practicum.dto.compilationDto.UpdateCompilationRequest;
import ru.practicum.dto.eventDto.EventShortDto;
import ru.practicum.dto.requestDto.ParticipationRequestDto;
import ru.practicum.dto.requestDto.RequestStatus;
import ru.practicum.dto.userDto.UserDto;
import ru.practicum.dto.userDto.UserShortDto;
import ru.practicum.event.mapper.CompilationMapper;
import ru.practicum.event.mapper.EventMapper;
import ru.practicum.event.model.Compilation;
import ru.practicum.event.model.Event;
import ru.practicum.event.service.AdminCompilationService;
import ru.practicum.feign.RequestServiceFeign;
import ru.practicum.feign.UserServiceFeign;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/admin/compilations")
@RequiredArgsConstructor
@Validated
@Slf4j
public class AdminCompilationController {

    private final AdminCompilationService adminCompilationService;
    private final RequestServiceFeign requestServiceFeign;
    private final UserServiceFeign userServiceFeign;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CompilationDto createCompilation(@Valid @RequestBody NewCompilationDto dto) {
        log.info("POST /admin/compilations - создание подборки");
        Compilation compilation = adminCompilationService.createCompilation(dto);
        return buildCompilationDto(compilation);
    }

    @DeleteMapping("/{compId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCompilation(@PathVariable @Positive Long compId) {
        log.info("DELETE /admin/compilations/{}", compId);
        adminCompilationService.deleteCompilation(compId);
    }

    @PatchMapping("/{compId}")
    public CompilationDto updateCompilation(@PathVariable @Positive Long compId,
                                            @Valid @RequestBody UpdateCompilationRequest request) {
        log.info("PATCH /admin/compilations/{} - обновление подборки", compId);
        Compilation compilation = adminCompilationService.updateCompilation(compId, request);
        return buildCompilationDto(compilation);
    }

    private CompilationDto buildCompilationDto(Compilation compilation) {
        List<Event> events = compilation.getEvents();

        Map<Long, Long> viewsMap = adminCompilationService.getViewsForEvents(events);
        Map<Long, Long> confirmedMap = getConfirmedRequestsCounts(events);
        Map<Long, UserShortDto> initiatorMap = getEventInitiators(events);

        List<EventShortDto> eventShortDtos = events.stream()
                .map(event -> {
                    Long confirmed = confirmedMap.getOrDefault(event.getId(), 0L);
                    Long views = viewsMap.getOrDefault(event.getId(), 0L);
                    UserShortDto initiator = initiatorMap.get(event.getInitiatorId());
                    return EventMapper.toShortDto(event, confirmed, views, initiator);
                })
                .collect(Collectors.toList());

        return CompilationMapper.toDto(compilation, eventShortDtos);
    }

    private Map<Long, Long> getConfirmedRequestsCounts(List<Event> events) {
        if (events == null || events.isEmpty()) return Map.of();
        List<Long> eventIds = events.stream().map(Event::getId).collect(Collectors.toList());
        return requestServiceFeign
                .getAllByEventIdInAndStatus(1L, eventIds, RequestStatus.CONFIRMED)
                .stream()
                .collect(Collectors.groupingBy(
                        ParticipationRequestDto::getEvent,
                        Collectors.counting()
                ));
    }

    private Map<Long, UserShortDto> getEventInitiators(List<Event> events) {
        if (events == null || events.isEmpty()) return Map.of();
        List<Long> userIds = events.stream()
                .map(Event::getInitiatorId)
                .distinct()
                .collect(Collectors.toList());
        List<UserDto> users = userServiceFeign.getAllUsersById(userIds);
        return users.stream()
                .collect(Collectors.toMap(
                        UserDto::getId,
                        user -> new UserShortDto(user.getId(), user.getName())
                ));
    }
}