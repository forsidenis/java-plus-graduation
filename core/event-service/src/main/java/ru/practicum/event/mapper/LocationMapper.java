package ru.practicum.event.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;
import ru.practicum.dto.eventDto.LocationDto;
import ru.practicum.event.model.Location;

@Mapper
public interface LocationMapper {
    LocationMapper INSTANCE = Mappers.getMapper(LocationMapper.class);

    LocationDto toDto(Location location);

    Location toLocation(LocationDto locationDto);
}