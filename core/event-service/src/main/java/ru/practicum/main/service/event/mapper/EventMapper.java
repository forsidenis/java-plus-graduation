package ru.practicum.main.service.event.mapper;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ru.practicum.common.dto.*;
import ru.practicum.main.service.event.model.Event;
import ru.practicum.main.service.event.model.Location;

import java.time.LocalDateTime;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class EventMapper {

    public static Event toEvent(NewEventDto dto, Long categoryId, Long initiatorId) {
        Event event = Event.builder()
                .annotation(dto.getAnnotation())
                .categoryId(categoryId)
                .createdOn(LocalDateTime.now())
                .description(dto.getDescription())
                .eventDate(dto.getEventDate())
                .initiatorId(initiatorId)
                .paid(dto.getPaid())
                .participantLimit(dto.getParticipantLimit())
                .requestModeration(dto.getRequestModeration())
                .state(EventState.PENDING)
                .title(dto.getTitle())
                .build();
        if (dto.getLocation() != null) {
            event.setLocation(new Location(dto.getLocation().getLat(), dto.getLocation().getLon()));
        }
        return event;
    }

    public static EventFullDto toFullDto(Event event, CategoryDto category, UserShortDto initiator,
                                         Long confirmedRequests, Long views) {
        if (event == null) return null;
        if (initiator == null) {
            initiator = UserShortDto.builder()
                    .id(event.getInitiatorId())
                    .name("dummy_user_" + event.getInitiatorId())
                    .build();
        }
        if (category == null) {
            category = CategoryDto.builder()
                    .id(event.getCategoryId())
                    .name("dummy_category_" + event.getCategoryId())
                    .build();
        }
        return EventFullDto.builder()
                .id(event.getId())
                .annotation(event.getAnnotation())
                .category(category)
                .confirmedRequests(confirmedRequests != null ? confirmedRequests : 0L)
                .createdOn(event.getCreatedOn())
                .description(event.getDescription())
                .eventDate(event.getEventDate())
                .initiator(initiator)
                .paid(event.getPaid())
                .participantLimit(event.getParticipantLimit())
                .publishedOn(event.getPublishedOn())
                .requestModeration(event.getRequestModeration())
                .state(event.getState())
                .title(event.getTitle())
                .views(views != null ? views : 0L)
                .location(event.getLocation() != null ?
                        new LocationDto(event.getLocation().getLat(), event.getLocation().getLon()) : null)
                .build();
    }

    public static EventShortDto toShortDto(Event event, CategoryDto category, UserShortDto initiator,
                                           Long confirmedRequests, Long views) {
        if (event == null) return null;
        if (initiator == null) {
            initiator = UserShortDto.builder()
                    .id(event.getInitiatorId())
                    .name("dummy_user_" + event.getInitiatorId())
                    .build();
        }
        if (category == null) {
            category = CategoryDto.builder()
                    .id(event.getCategoryId())
                    .name("dummy_category_" + event.getCategoryId())
                    .build();
        }
        return EventShortDto.builder()
                .id(event.getId())
                .annotation(event.getAnnotation())
                .category(category)
                .confirmedRequests(confirmedRequests != null ? confirmedRequests : 0L)
                .eventDate(event.getEventDate())
                .initiator(initiator)
                .paid(event.getPaid())
                .title(event.getTitle())
                .views(views != null ? views : 0L)
                .build();
    }

    public static EventShortDto toShortDto(Event event) {
        if (event == null) return null;
        return EventShortDto.builder()
                .id(event.getId())
                .annotation(event.getAnnotation())
                .confirmedRequests(0L)
                .eventDate(event.getEventDate())
                .paid(event.getPaid())
                .title(event.getTitle())
                .views(0L)
                .build();
    }

    public static void updateEventFromUserRequest(Event event, UpdateEventUserRequest dto, Long categoryId) {
        if (dto.getAnnotation() != null) event.setAnnotation(dto.getAnnotation());
        if (categoryId != null) event.setCategoryId(categoryId);
        if (dto.getDescription() != null) event.setDescription(dto.getDescription());
        if (dto.getEventDate() != null) event.setEventDate(dto.getEventDate());
        if (dto.getLocation() != null) {
            if (event.getLocation() == null) event.setLocation(new Location());
            event.getLocation().setLat(dto.getLocation().getLat());
            event.getLocation().setLon(dto.getLocation().getLon());
        }
        if (dto.getPaid() != null) event.setPaid(dto.getPaid());
        if (dto.getParticipantLimit() != null) event.setParticipantLimit(dto.getParticipantLimit());
        if (dto.getRequestModeration() != null) event.setRequestModeration(dto.getRequestModeration());
        if (dto.getTitle() != null) event.setTitle(dto.getTitle());
    }

    public static void updateEventFromAdminRequest(Event event, UpdateEventAdminRequest dto, Long categoryId) {
        if (dto.getAnnotation() != null) event.setAnnotation(dto.getAnnotation());
        if (categoryId != null) event.setCategoryId(categoryId);
        if (dto.getDescription() != null) event.setDescription(dto.getDescription());
        if (dto.getEventDate() != null) event.setEventDate(dto.getEventDate());
        if (dto.getLocation() != null) {
            if (event.getLocation() == null) event.setLocation(new Location());
            event.getLocation().setLat(dto.getLocation().getLat());
            event.getLocation().setLon(dto.getLocation().getLon());
        }
        if (dto.getPaid() != null) event.setPaid(dto.getPaid());
        if (dto.getParticipantLimit() != null) event.setParticipantLimit(dto.getParticipantLimit());
        if (dto.getRequestModeration() != null) event.setRequestModeration(dto.getRequestModeration());
        if (dto.getTitle() != null) event.setTitle(dto.getTitle());
    }
}