package ru.practicum.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import ru.practicum.mapper.CompilationMapper;
import ru.practicum.model.Compilation;
import ru.practicum.service.AdminCompilationService;
import ru.practicum.dto.compilationDto.CompilationDto;
import ru.practicum.dto.compilationDto.NewCompilationDto;
import ru.practicum.dto.compilationDto.UpdateCompilationRequest;
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
@RequestMapping("/admin/compilations")
@RequiredArgsConstructor
@Validated
@Slf4j
public class AdminCompilationController {

    private final AdminCompilationService adminCompilationService;
    private final EventServiceFeign eventServiceFeign;
    private final RequestServiceFeign requestServiceFeign;
    private final UserServiceFeign userServiceFeign;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CompilationDto createCompilation(@Valid @RequestBody NewCompilationDto newCompilationDto) {
        log.info("POST /admin/compilations - создание подборки");
        Compilation compilation = adminCompilationService.createCompilation(newCompilationDto);
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
        List<Long> eventIds = compilation.getEventIds();
        List<EventShortDto> eventDtos = eventServiceFeign.getEventsByIds(eventIds);

        Map<Long, Long> viewsMap = adminCompilationService.getViewsForEvents(eventIds);
        Map<Long, Long> confirmedMap = getConfirmedRequestsCounts(eventIds);
        Map<Long, UserShortDto> initiatorMap = getEventInitiators(eventIds);

        eventDtos = eventDtos.stream()
                .map(dto -> {
                    Long confirmed = confirmedMap.getOrDefault(dto.getId(), 0L);
                    Long views = viewsMap.getOrDefault(dto.getId(), 0L);
                    UserShortDto initiator = initiatorMap.get(dto.getInitiator().getId());
                    // Обновляем поля (можно создать новый объект)
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
        return requestServiceFeign
                .getAllByEventIdInAndStatus(1L, eventIds, RequestStatus.CONFIRMED)
                .stream()
                .collect(Collectors.groupingBy(
                        ParticipationRequestDto::getEvent,
                        Collectors.counting()
                ));
    }

    private Map<Long, UserShortDto> getEventInitiators(List<Long> eventIds) {
        if (eventIds == null || eventIds.isEmpty()) return Map.of();
        // Получаем события и из них инициаторов
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
    }
}