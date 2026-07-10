package ru.practicum.request.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.practicum.common.dto.RequestStatus;
import ru.practicum.request.model.ParticipationRequest;

import java.util.List;
import java.util.Optional;

public interface RequestRepository extends JpaRepository<ParticipationRequest, Integer> {

    List<ParticipationRequest> findAllByRequesterId(Integer userId);

    Optional<ParticipationRequest> findByIdAndRequesterId(Integer requestId, Integer userId);

    Optional<ParticipationRequest> findByEventIdAndRequesterId(Integer eventId, Integer userId);

    List<ParticipationRequest> findAllByEventId(Integer eventId);

    List<ParticipationRequest> findAllByIdIn(List<Integer> ids);

    boolean existsByEventIdAndRequesterIdAndStatus(Integer eventId, Integer requesterId, RequestStatus status);

    @Query("SELECT COUNT(r) FROM ParticipationRequest r WHERE r.eventId = :eventId AND r.status = :status")
    long countByEventIdAndStatus(@Param("eventId") Long eventId,
                                 @Param("status") RequestStatus status);

    List<ParticipationRequest> findAllByEventIdInAndStatus(List<Long> eventIds, RequestStatus status);


}