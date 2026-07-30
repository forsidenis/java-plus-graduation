package ru.practicum.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import ru.practicum.ewm.stats.avro.ActionTypeAvro;
import ru.practicum.ewm.stats.avro.EventSimilarityAvro;
import ru.practicum.ewm.stats.avro.UserActionAvro;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class AggregatorService {

    private final KafkaTemplate<Long, EventSimilarityAvro> similarityKafkaTemplate;
    private static final String SIMILARITY_TOPIC = "stats.events-similarity.v1";

    // Хранилище весов: Map<eventId, Map<userId, Double>>
    private final Map<Long, Map<Long, Double>> userWeights = new HashMap<>();
    // Хранилище общих сумм весов: Map<eventId, Double>
    private final Map<Long, Double> totalSums = new HashMap<>();
    // Хранилище сумм минимальных весов для пар: Map<eventA, Map<eventB, Double>> (eventA < eventB)
    private final Map<Long, Map<Long, Double>> minSums = new HashMap<>();

    @KafkaListener(topics = "stats.user-actions.v1", groupId = "aggregator")
    public void consume(UserActionAvro action) {
        long userId = action.getUserId();
        long eventId = action.getEventId();
        double newWeight = getWeight(action.getActionType());
        double oldWeight = getOldWeight(userId, eventId);

        if (newWeight <= oldWeight) {
            log.debug("Новый вес {} не больше старого {}, пересчёт не требуется", newWeight, oldWeight);
            return;
        }

        // Обновляем вес пользователя для мероприятия
        updateWeight(userId, eventId, newWeight, oldWeight);

        // Пересчитываем сходство для всех пар, где участвует eventId
        recalculateSimilarities(eventId);
    }

    private double getWeight(ActionTypeAvro actionType) {
        return switch (actionType) {
            case VIEW -> 0.4;
            case REGISTER -> 0.8;
            case LIKE -> 1.0;
            default -> 0.0;
        };
    }

    private double getOldWeight(long userId, long eventId) {
        Map<Long, Double> userMap = userWeights.get(eventId);
        if (userMap == null) return 0.0;
        return userMap.getOrDefault(userId, 0.0);
    }

    private void updateWeight(long userId, long eventId, double newWeight, double oldWeight) {
        // Обновляем вес пользователя
        Map<Long, Double> userMap = userWeights.computeIfAbsent(eventId, k -> new HashMap<>());
        userMap.put(userId, newWeight);

        // Обновляем общую сумму для eventId
        double delta = newWeight - oldWeight;
        totalSums.put(eventId, totalSums.getOrDefault(eventId, 0.0) + delta);
    }

    private void recalculateSimilarities(long eventId) {
        // Для каждого другого мероприятия, с которым есть общие пользователи
        Map<Long, Double> usersOfEvent = userWeights.getOrDefault(eventId, new HashMap<>());

        for (Map.Entry<Long, Map<Long, Double>> otherEntry : userWeights.entrySet()) {
            long otherEventId = otherEntry.getKey();
            if (otherEventId == eventId) continue;

            Map<Long, Double> usersOfOther = otherEntry.getValue();
            // Находим общих пользователей
            double minSum = 0.0;
            for (Long userId : usersOfEvent.keySet()) {
                if (usersOfOther.containsKey(userId)) {
                    double w1 = usersOfEvent.get(userId);
                    double w2 = usersOfOther.get(userId);
                    minSum += Math.min(w1, w2);
                }
            }

            // Обновляем minSums для пары (eventId, otherEventId)
            long first = Math.min(eventId, otherEventId);
            long second = Math.max(eventId, otherEventId);
            Map<Long, Double> innerMap = minSums.computeIfAbsent(first, k -> new HashMap<>());
            innerMap.put(second, minSum);

            // Рассчитываем косинусное сходство
            double sumA = totalSums.getOrDefault(first, 0.0);
            double sumB = totalSums.getOrDefault(second, 0.0);
            if (sumA == 0 || sumB == 0) continue;
            double similarity = minSum / (Math.sqrt(sumA) * Math.sqrt(sumB));

            // Используем timestamp из действия, которое вызвало пересчёт – берём текущее время
            // Можно было бы взять из action, но у нас action не передаётся. Возьмём текущее.
            long timestamp = System.currentTimeMillis();

            // Отправляем в Kafka
            EventSimilarityAvro similarityMsg = EventSimilarityAvro.newBuilder()
                    .setEventA(first)
                    .setEventB(second)
                    .setScore(similarity)
                    .setTimestamp(Instant.ofEpochMilli(timestamp))
                    .build();
            similarityKafkaTemplate.send(SIMILARITY_TOPIC, first, similarityMsg);
            log.debug("Отправлено сходство для пары ({}, {}) = {}", first, second, similarity);
        }
    }
}