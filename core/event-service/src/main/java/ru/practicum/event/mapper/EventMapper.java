package ru.practicum.event.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;
import ru.practicum.dto.eventDto.EventFullDto;
import ru.practicum.dto.eventDto.EventShortDto;
import ru.practicum.dto.eventDto.NewEventDto;
import ru.practicum.dto.userDto.UserDto;
import ru.practicum.dto.userDto.UserShortDto;
import ru.practicum.event.model.Category;
import ru.practicum.event.model.Event;

@Mapper(uses = {CategoryMapper.class, LocationMapper.class})
public interface EventMapper {
    EventMapper INSTANCE = Mappers.getMapper(EventMapper.class);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdOn", expression = "java(java.time.LocalDateTime.now())")
    @Mapping(target = "state", constant = "PENDING")
    @Mapping(target = "initiatorId", source = "initiator.id")
    @Mapping(target = "location", source = "dto.location")
    @Mapping(target = "category", source = "category")
    @Mapping(target = "publishedOn", ignore = true)
    Event toEvent(NewEventDto dto, Category category, UserDto initiator);

    @Mapping(target = "initiator", source = "initiator")
    @Mapping(target = "confirmedRequests", source = "confirmedRequests")
    @Mapping(target = "views", source = "views")
    @Mapping(target = "category", source = "event.category")
    @Mapping(target = "id", source = "event.id")
    EventFullDto toFullDto(Event event, Long confirmedRequests, Long views, UserShortDto initiator);

    @Mapping(target = "initiator", source = "initiator")
    @Mapping(target = "confirmedRequests", source = "confirmedRequests")
    @Mapping(target = "views", source = "views")
    @Mapping(target = "category", source = "event.category")
    @Mapping(target = "id", source = "event.id")
    EventShortDto toShortDto(Event event, Long confirmedRequests, Long views, UserShortDto initiator);
}