package ru.practicum.request.mapper;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ru.practicum.common.dto.ParticipationRequestDto;
import ru.practicum.common.dto.RequestStatus;
import ru.practicum.request.model.ParticipationRequest;

import java.time.LocalDateTime;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class RequestMapper {

    public static ParticipationRequestDto toDto(ParticipationRequest request) {
        if (request == null) return null;
        return ParticipationRequestDto.builder()
                .id(request.getId())
                .created(request.getCreated())
                .event(request.getEventId().intValue())
                .requester(request.getRequesterId().intValue())
                .status(request.getStatus())
                .build();
    }

    public static ParticipationRequest toEntity(Long eventId, Long requesterId, RequestStatus status) {
        return ParticipationRequest.builder()
                .created(LocalDateTime.now())
                .eventId(eventId)
                .requesterId(requesterId)
                .status(status)
                .build();
    }
}