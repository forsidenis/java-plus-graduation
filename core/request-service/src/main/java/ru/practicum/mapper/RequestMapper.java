package ru.practicum.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;
import ru.practicum.dto.eventDto.EventFullDto;
import ru.practicum.dto.requestDto.ParticipationRequestDto;
import ru.practicum.model.ParticipationRequest;

@Mapper
public interface RequestMapper {
    RequestMapper INSTANCE = Mappers.getMapper(RequestMapper.class);

    @Mapping(target = "event", source = "eventId")
    @Mapping(target = "requester", source = "requesterId")
    ParticipationRequestDto toDto(ParticipationRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "created", expression = "java(java.time.LocalDateTime.now())")
    @Mapping(target = "eventId", source = "event.id")
    @Mapping(target = "requesterId", source = "userId")
    @Mapping(target = "status", constant = "PENDING")
    ParticipationRequest toNewRequest(EventFullDto event, Long userId);
}