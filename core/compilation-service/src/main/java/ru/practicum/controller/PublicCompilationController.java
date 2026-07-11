package ru.practicum.controller;

import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import ru.practicum.mapper.CompilationMapper;
import ru.practicum.model.Compilation;
import ru.practicum.service.PublicCompilationService;
import ru.practicum.dto.compilationDto.CompilationDto;
import ru.practicum.dto.eventDto.EventShortDto;
import ru.practicum.dto.requestDto.ParticipationRequestDto;
import ru.practicum.dto.requestDto.RequestStatus;
import ru.practicum.dto.userDto.UserDto;
import ru.practicum.dto.userDto.UserShortDto;
import ru.practicum.faign.EventServiceFeign;
import ru.practicum.faign.RequestServiceFeign;
import ru.practicum.faign.UserServiceFeign;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/compilations")
@RequiredArgsConstructor
@Validated
@Slf4j
public class PublicCompilationController {

    private final PublicCompilationService publicCompilationService;
    private final EventServiceFeign eventServiceFeign;
    private final RequestServiceFeign requestServiceFeign;
    private final UserServiceFeign userServiceFeign;

    @GetMapping
    public List<CompilationDto> getCompilations(@RequestParam(required = false) Boolean pinned,
                                                @RequestParam(defaultValue = "0") int from,
                                                @RequestParam(defaultValue = "10") int size) {
        log.info("GET /compilations - получение подборок");
        List<Compilation> compilations = publicCompilationService.getCompilations(pinned, from, size);
        if (compilations.isEmpty()) return List.of();
        return compilations.stream().map(this::buildCompilationDto).collect(Collectors.toList());
    }

    @GetMapping("/{compId}")
    public CompilationDto getCompilationById(@PathVariable @Positive Long compId) {
        log.info("GET /compilations/{} - получение подборки", compId);
        Compilation compilation = publicCompilationService.getCompilationById(compId);
        return buildCompilationDto(compilation);
    }

    private CompilationDto buildCompilationDto(Compilation compilation) {
        List<Long> eventIds = compilation.getEventIds();
        List<EventShortDto> eventDtos = eventServiceFeign.getEventsByIds(eventIds);

        Map<Long, Long> viewsMap = publicCompilationService.getViewsForEvents(eventIds);
        Map<Long, Long> confirmedMap = getConfirmedRequestsCounts(eventIds);
        Map<Long, UserShortDto> initiatorMap = getEventInitiators(eventIds);

        eventDtos = eventDtos.stream()
                .map(dto -> {
                    Long confirmed = confirmedMap.getOrDefault(dto.getId(), 0L);
                    Long views = viewsMap.getOrDefault(dto.getId(), 0L);
                    UserShortDto initiator = initiatorMap.get(dto.getInitiator().getId());
                    return EventShortDto.builder()
                            .id(dto.getId())
                            .annotation(dto.getAnnotation())
                            .category(dto.getCategory())
                            .confirmedRequests(confirmed)
                            .eventDate(dto.getEventDate())
                            .initiator(initiator)
                            .paid(dto.getPaid())
                            .title(dto.getTitle())
                            .views(views)
                            .build();
                })
                .collect(Collectors.toList());

        return CompilationMapper.toDto(compilation, eventDtos);
    }

    private Map<Long, Long> getConfirmedRequestsCounts(List<Long> eventIds) {
        if (eventIds == null || eventIds.isEmpty()) return Map.of();
        try {
            return requestServiceFeign
                    .getAllByEventIdInAndStatus(1L, eventIds, RequestStatus.CONFIRMED)
                    .stream()
                    .collect(Collectors.groupingBy(
                            ParticipationRequestDto::getEvent,
                            Collectors.counting()
                    ));
        } catch (Exception e) {
            log.warn("Не удалось получить количество подтвержденных запросов: {}", e.getMessage());
            return Map.of();
        }
    }

    private Map<Long, UserShortDto> getEventInitiators(List<Long> eventIds) {
        if (eventIds == null || eventIds.isEmpty()) return Map.of();
        try {
            List<EventShortDto> events = eventServiceFeign.getEventsByIds(eventIds);
            List<Long> userIds = events.stream()
                    .map(e -> e.getInitiator().getId())
                    .distinct()
                    .collect(Collectors.toList());
            if (userIds.isEmpty()) return Map.of();
            List<UserDto> users = userServiceFeign.getAllUsersById(userIds);
            return users.stream()
                    .collect(Collectors.toMap(
                            UserDto::getId,
                            user -> new UserShortDto(user.getId(), user.getName())
                    ));
        } catch (Exception e) {
            log.warn("Не удалось получить данные пользователей: {}", e.getMessage());
            return Map.of();
        }
    }
}