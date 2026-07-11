package ru.practicum.compilation.mapper;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ru.practicum.compilation.model.Compilation;
import ru.practicum.dto.compilationDto.CompilationDto;
import ru.practicum.dto.compilationDto.NewCompilationDto;
import ru.practicum.dto.compilationDto.UpdateCompilationRequest;
import ru.practicum.dto.eventDto.EventShortDto;

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

    public static Compilation toEntity(NewCompilationDto dto, List<Long> eventIds) {
        return Compilation.builder()
                .title(dto.getTitle())
                .pinned(dto.getPinned() != null ? dto.getPinned() : false)
                .eventIds(eventIds != null ? eventIds : Collections.emptyList())
                .build();
    }

    public static void updateEntity(Compilation compilation, UpdateCompilationRequest request, List<Long> eventIds) {
        if (request.getTitle() != null) compilation.setTitle(request.getTitle());
        if (request.getPinned() != null) compilation.setPinned(request.getPinned());
        if (eventIds != null) compilation.setEventIds(eventIds);
    }
}