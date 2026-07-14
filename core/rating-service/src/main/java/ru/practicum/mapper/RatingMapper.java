package ru.practicum.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;
import ru.practicum.dto.ratingDto.EventRatingResponseDto;
import ru.practicum.dto.ratingDto.EventRatingStatsDto;
import ru.practicum.dto.ratingDto.EventRatingTopDto;
import ru.practicum.model.EventRating;

import java.time.LocalDateTime;

@Mapper
public interface RatingMapper {
    RatingMapper INSTANCE = Mappers.getMapper(RatingMapper.class);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "ratingType", source = "type")
    @Mapping(target = "created", source = "time")
    EventRating toEntity(Long eventId, Long userId, EventRating.RatingType type, LocalDateTime time);

    @Mapping(target = "ratingType", expression = "java(rating.getRatingType().name().toLowerCase())")
    @Mapping(target = "timestamp", source = "created")
    EventRatingResponseDto toResponseDto(EventRating rating);

    EventRatingStatsDto toStatsDto(Long eventId, Long likeCount, Long dislikeCount);

    default EventRatingTopDto toTopDto(Object[] data) {
        if (data == null || data.length < 2) {
            return null;
        }
        EventRatingTopDto dto = new EventRatingTopDto();
        dto.setEventId(((Number) data[0]).longValue());
        dto.setRatingCount(((Number) data[1]).longValue());
        return dto;
    }
}