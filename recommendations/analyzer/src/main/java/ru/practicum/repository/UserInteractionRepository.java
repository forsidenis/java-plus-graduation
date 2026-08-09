package ru.practicum.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.practicum.model.UserInteraction;

import java.util.List;
import java.util.Optional;

public interface UserInteractionRepository extends JpaRepository<UserInteraction, Long> {
    Optional<UserInteraction> findByUserIdAndEventId(Long userId, Long eventId);

    List<UserInteraction> findAllByUserId(Long userId);

    @Query("SELECT SUM(i.weight) FROM UserInteraction i WHERE i.eventId IN :eventIds")
    Double sumWeightsByEventIds(@Param("eventIds") List<Long> eventIds);

    @Query("SELECT i.eventId, SUM(i.weight) FROM UserInteraction i WHERE i.eventId IN :eventIds GROUP BY i.eventId")
    List<Object[]> sumWeightsGroupedByEventIds(@Param("eventIds") List<Long> eventIds);
}