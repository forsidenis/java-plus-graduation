package ru.practicum.event.mapper;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ru.practicum.dto.compilationDto.CompilationDto;
import ru.practicum.dto.compilationDto.NewCompilationDto;
import ru.practicum.dto.compilationDto.UpdateCompilationRequest;
import ru.practicum.dto.eventDto.EventShortDto;
import ru.practicum.event.model.Compilation;
import ru.practicum.event.model.Event;

import java.util.Collections;
import java.util.List;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class CompilationMapper {

    public static CompilationDto toDto(Compilation compilation, List<EventShortDto> eventShortDtos) {
        return CompilationDto.builder()
                .id(compilation.getId())
                .title(compilation.getTitle())
                .pinned(compilation.getPinned())
                .events(eventShortDtos != null ? eventShortDtos : Collections.emptyList())
                .build();
    }

    public static Compilation toEntity(NewCompilationDto dto, List<Event> events) {
        return Compilation.builder()
                .title(dto.getTitle())
                .pinned(dto.getPinned() != null ? dto.getPinned() : false)
                .events(events != null ? events : Collections.emptyList())
                .build();
    }

    public static void updateEntity(Compilation compilation, UpdateCompilationRequest request, List<Event> events) {
        if (request.getTitle() != null) {
            compilation.setTitle(request.getTitle());
        }
        if (request.getPinned() != null) {
            compilation.setPinned(request.getPinned());
        }
        if (events != null) {
            compilation.setEvents(events);
        }
    }
}