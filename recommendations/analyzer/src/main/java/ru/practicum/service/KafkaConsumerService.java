package ru.practicum.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.ewm.stats.avro.ActionTypeAvro;
import ru.practicum.ewm.stats.avro.EventSimilarityAvro;
import ru.practicum.ewm.stats.avro.UserActionAvro;
import ru.practicum.model.EventSimilarity;
import ru.practicum.model.UserInteraction;
import ru.practicum.repository.EventSimilarityRepository;
import ru.practicum.repository.UserInteractionRepository;

import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class KafkaConsumerService {

    private final UserInteractionRepository userInteractionRepository;
    private final EventSimilarityRepository eventSimilarityRepository;

    @KafkaListener(topics = "stats.user-actions.v1", groupId = "analyzer")
    @Transactional
    public void consumeUserAction(UserActionAvro action) {
        long userId = action.getUserId();
        long eventId = action.getEventId();
        double weight = getWeight(action.getActionType());

        Optional<UserInteraction> existingOpt = userInteractionRepository.findByUserIdAndEventId(userId, eventId);
        if (existingOpt.isPresent()) {
            UserInteraction existing = existingOpt.get();
            if (existing.getWeight() >= weight) {
                log.debug("Новый вес {} не больше существующего {}, пропускаем", weight, existing.getWeight());
                return;
            }
            existing.setWeight(weight);
            // Преобразуем Instant в миллисекунды
            existing.setLastActionAt(action.getTimestamp().toEpochMilli());
        } else {
            UserInteraction interaction = UserInteraction.builder()
                    .userId(userId)
                    .eventId(eventId)
                    .weight(weight)
                    .lastActionAt(action.getTimestamp().toEpochMilli())
                    .build();
            userInteractionRepository.save(interaction);
        }
        log.info("Обновлено взаимодействие: userId={}, eventId={}, weight={}", userId, eventId, weight);
    }

    @KafkaListener(topics = "stats.events-similarity.v1", groupId = "analyzer")
    @Transactional
    public void consumeEventSimilarity(EventSimilarityAvro similarity) {
        long eventA = similarity.getEventA();
        long eventB = similarity.getEventB();
        double score = similarity.getScore();

        Optional<EventSimilarity> existingOpt = eventSimilarityRepository.findByEventAAndEventB(eventA, eventB);
        if (existingOpt.isPresent()) {
            EventSimilarity existing = existingOpt.get();
            existing.setScore(score);
            // Преобразуем Instant в миллисекунды
            existing.setUpdatedAt(similarity.getTimestamp().toEpochMilli());
        } else {
            EventSimilarity newSim = EventSimilarity.builder()
                    .eventA(eventA)
                    .eventB(eventB)
                    .score(score)
                    .updatedAt(similarity.getTimestamp().toEpochMilli())
                    .build();
            eventSimilarityRepository.save(newSim);
        }
        log.info("Обновлено сходство: ({}, {}) = {}", eventA, eventB, score);
    }

    private double getWeight(ActionTypeAvro actionType) {
        return switch (actionType) {
            case VIEW -> 0.4;
            case REGISTER -> 0.8;
            case LIKE -> 1.0;
            default -> 0.0;
        };
    }
}