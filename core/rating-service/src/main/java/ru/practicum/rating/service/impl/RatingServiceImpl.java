package ru.practicum.rating.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.common.dto.*;
import ru.practicum.common.exception.NotFoundException;
import ru.practicum.common.exception.ConditionsNotMetException;
import ru.practicum.rating.client.EventClient;
import ru.practicum.rating.client.RequestClient;
import ru.practicum.rating.client.UserClient;
import ru.practicum.rating.mapper.RatingMapper;
import ru.practicum.rating.model.EventRating;
import ru.practicum.rating.repository.EventRatingRepository;
import ru.practicum.rating.service.RatingService;

import java.util.List;

@Service
@Slf4j
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class RatingServiceImpl implements RatingService {

    private final EventRatingRepository ratingRepository;
    private final UserClient userClient;
    private final EventClient eventClient;
    private final RequestClient requestClient;

    @Override
    @Transactional
    public EventRatingResponseDto addLike(Long userId, Long eventId, EventRatingRequestDto dto) {
        return addRating(userId, eventId, dto, EventRating.RatingType.LIKE);
    }

    @Override
    @Transactional
    public EventRatingResponseDto addDislike(Long userId, Long eventId, EventRatingRequestDto dto) {
        return addRating(userId, eventId, dto, EventRating.RatingType.DISLIKE);
    }

    private EventRatingResponseDto addRating(Long userId, Long eventId, EventRatingRequestDto dto,
                                             EventRating.RatingType type) {
        log.info("addRating: userId={}, eventId={}, type={}", userId, eventId, type);
        UserShortDto user = userClient.getUser(userId);
        if (user == null) throw new NotFoundException("User not found");
        EventFullDto event = eventClient.getEvent(eventId);
        if (event == null) throw new NotFoundException("Event not found");
        if (event.getEventDate().isAfter(dto.getTimestamp())) {
            throw new ConditionsNotMetException("Event not yet occurred");
        }
        boolean attended = requestClient.existsByEventAndUserAndStatusConfirmed(eventId, userId);
        if (!attended) {
            throw new ConditionsNotMetException("User did not attend event");
        }
        EventRating existing = ratingRepository.findByEventIdAndUserId(eventId, userId).orElse(null);
        if (existing != null) {
            if (existing.getRatingType() == type) {
                throw new ConditionsNotMetException("Already rated");
            }
            existing.setRatingType(type);
            existing.setCreated(dto.getTimestamp());
            ratingRepository.save(existing);
            return RatingMapper.toResponseDto(existing);
        }
        EventRating newRating = RatingMapper.toEntity(eventId, userId, type, dto.getTimestamp());
        newRating = ratingRepository.save(newRating);
        return RatingMapper.toResponseDto(newRating);
    }

    @Override
    @Transactional
    public void deleteRating(Long userId, Long eventId) {
        log.info("deleteRating: userId={}, eventId={}", userId, eventId);
        EventRating rating = ratingRepository.findByEventIdAndUserId(eventId, userId)
                .orElseThrow(() -> new NotFoundException("Rating not found"));
        ratingRepository.delete(rating);
    }

    @Override
    public EventRatingStatsDto getEventRatingStats(Long eventId) {
        log.info("getEventRatingStats: eventId={}", eventId);
        // проверка существования события через клиент
        try {
            eventClient.getEvent(eventId);
        } catch (Exception e) {
            throw new NotFoundException("Event not found");
        }
        List<Object[]> data = ratingRepository.getRatingStatsByEventId(eventId);
        if (data.isEmpty()) return RatingMapper.toStatsDto(eventId, 0L, 0L);
        Object[] row = data.get(0);
        return RatingMapper.toStatsDto(eventId, ((Number) row[1]).longValue(), ((Number) row[2]).longValue());
    }

    @Override
    public EventRatingListDto getUserRatings(Long userId, String rating, int from, int size) {
        log.info("getUserRatings: userId={}", userId);
        if (userClient.getUser(userId) == null) throw new NotFoundException("User not found");
        Pageable pageable = PageRequest.of(from / size, size);
        List<EventRating> ratings;
        long total;
        if (rating == null || rating.isEmpty()) {
            ratings = ratingRepository.findByUserIdOrderByCreatedDesc(userId, pageable);
            total = ratingRepository.countByUserId(userId);
        } else if ("like".equalsIgnoreCase(rating)) {
            ratings = ratingRepository.findByUserIdAndRatingTypeOrderByCreatedDesc(userId, EventRating.RatingType.LIKE, pageable);
            total = ratingRepository.countByUserIdAndRatingType(userId, EventRating.RatingType.LIKE);
        } else if ("dislike".equalsIgnoreCase(rating)) {
            ratings = ratingRepository.findByUserIdAndRatingTypeOrderByCreatedDesc(userId, EventRating.RatingType.DISLIKE, pageable);
            total = ratingRepository.countByUserIdAndRatingType(userId, EventRating.RatingType.DISLIKE);
        } else {
            throw new ConditionsNotMetException("rating must be 'like' or 'dislike'");
        }
        return EventRatingListDto.builder()
                .ratings(ratings.stream().map(RatingMapper::toResponseDto).toList())
                .totalElements(total)
                .totalPages((int) Math.ceil((double) total / size))
                .currentPage(from / size)
                .pageSize(size)
                .build();
    }

    @Override
    public List<EventRatingTopDto> getTopRatedEvents(int from, int size, String order) {
        log.info("getTopRatedEvents");
        Sort.Direction dir = (order == null || order.equalsIgnoreCase("DESC")) ? Sort.Direction.DESC : Sort.Direction.ASC;
        Pageable pageable = PageRequest.of(from / size, size, Sort.by(dir, "rating"));
        List<Object[]> top = ratingRepository.findTopRatedEvents(pageable);
        return RatingMapper.toTopDtoList(top);
    }
}