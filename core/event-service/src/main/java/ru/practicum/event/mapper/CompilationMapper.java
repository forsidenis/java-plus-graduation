package ru.practicum.event.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.factory.Mappers;
import ru.practicum.dto.compilationDto.CompilationDto;
import ru.practicum.dto.compilationDto.NewCompilationDto;
import ru.practicum.dto.compilationDto.UpdateCompilationRequest;
import ru.practicum.dto.eventDto.EventShortDto;
import ru.practicum.event.model.Compilation;
import ru.practicum.event.model.Event;

import java.util.List;

@Mapper
public interface CompilationMapper {
    CompilationMapper INSTANCE = Mappers.getMapper(CompilationMapper.class);

    @Mapping(target = "events", source = "eventShortDtos")
    CompilationDto toDto(Compilation compilation, List<EventShortDto> eventShortDtos);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "events", source = "eventList")
    @Mapping(target = "pinned", source = "dto.pinned", defaultValue = "false")
    Compilation toEntity(NewCompilationDto dto, List<Event> eventList);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "title", source = "request.title")
    @Mapping(target = "pinned", source = "request.pinned")
    @Mapping(target = "events", source = "events")
    void updateEntity(@MappingTarget Compilation compilation, UpdateCompilationRequest request, List<Event> events);
}