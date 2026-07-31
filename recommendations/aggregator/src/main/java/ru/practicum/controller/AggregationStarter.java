package ru.practicum.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.errors.WakeupException;
import org.springframework.stereotype.Component;
import ru.practicum.client.ClientConfiguration;
import ru.practicum.ewm.stats.avro.ActionTypeAvro;
import ru.practicum.ewm.stats.avro.EventSimilarityAvro;
import ru.practicum.ewm.stats.avro.UserActionAvro;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class AggregationStarter {
    private final ClientConfiguration client;
    private final Map<Long, Map<Long, Double>> eventUserActionMatrix = new HashMap<>();
    private final Map<Long, Double> eventSumValue = new HashMap<>();
    private final Map<Long, Map<Long, Double>> minWeightsSums = new HashMap<>();

    public void start() {
        try {
            client.getConsumer().subscribe(List.of("stats.user-actions.v1"));

            while (true) {
                ConsumerRecords<String, UserActionAvro> records =
                        client.getConsumer().poll(Duration.ofSeconds(1));

                for (ConsumerRecord<String, UserActionAvro> record : records) {
                    processUserAction(record.value());
                }
            }
        } catch (WakeupException ignored) {
        } catch (Exception e) {
            log.error("Ошибка во время обработки событий от датчиков", e);
        } finally {
            closeResources();
        }
    }

    private void processUserAction(UserActionAvro data) {
        log.info("------------------------------");
        log.info("Получены данные: {}", data);

        long eventId = data.getEventId();
        long userId = data.getUserId();

        double oldWeight = getUserWeight(eventId, userId);
        double newWeight = computeWeightActionType(data.getActionType());

        if (newWeight <= oldWeight) {
            log.info("Новый вес {} не превышает старый {}, пересчет не требуется", newWeight, oldWeight);
            return;
        }

        updateUserWeight(eventId, userId, newWeight);
        updateEventSum(eventId, oldWeight, newWeight);
        recalculateSimilarities(eventId, userId, oldWeight, newWeight);
    }

    private double getUserWeight(long eventId, long userId) {
        Map<Long, Double> userWeights = eventUserActionMatrix.get(eventId);
        return userWeights != null ? userWeights.getOrDefault(userId, 0.0) : 0.0;
    }

    private void updateUserWeight(long eventId, long userId, double newWeight) {
        eventUserActionMatrix
                .computeIfAbsent(eventId, k -> new HashMap<>())
                .put(userId, newWeight);
        log.info("Обновлена матрица действий пользователя для события {}: пользователь {} -> вес {}",
                eventId, userId, newWeight);
    }

    private void updateEventSum(long eventId, double oldWeight, double newWeight) {
        double deltaEvent = newWeight - oldWeight;
        double currentEventSum = eventSumValue.getOrDefault(eventId, 0.0);
        double newEventSum = currentEventSum + deltaEvent;
        eventSumValue.put(eventId, newEventSum);
        log.info("Обновлена сумма весов для события {}: {} -> {}",
                eventId, currentEventSum, newEventSum);
    }

    private void recalculateSimilarities(long eventId, long userId, double oldWeight, double newWeight) {
        for (long otherEventId : eventSumValue.keySet()) {
            if (otherEventId == eventId) {
                continue;
            }

            double otherUserWeight = getUserWeight(otherEventId, userId);

            if (otherUserWeight == 0.0) {
                continue;
            }

            long firstKey = Math.min(eventId, otherEventId);
            long secondKey = Math.max(eventId, otherEventId);

            double sumFirst = getEventSum(firstKey);
            double sumSecond = getEventSum(secondKey);

            if (sumFirst <= 0 || sumSecond <= 0) {
                continue;
            }

            double minValue = Math.min(newWeight, otherUserWeight);
            double currentMinSum = getMinSum(firstKey, secondKey);
            double updatedMinSum = currentMinSum + (minValue - Math.min(oldWeight, otherUserWeight));

            minWeightsSums
                    .computeIfAbsent(firstKey, k -> new HashMap<>())
                    .put(secondKey, updatedMinSum);

            log.info("Обновлена S_min для пары ({}, {}): {}", firstKey, secondKey, updatedMinSum);
            sendSimilarityEvent(firstKey, secondKey, updatedMinSum, sumFirst, sumSecond);
        }
    }

    private double getEventSum(long eventId) {
        return eventSumValue.getOrDefault(eventId, 0.0);
    }

    private double getMinSum(long firstKey, long secondKey) {
        Map<Long, Double> innerMap = minWeightsSums.get(firstKey);
        return innerMap != null ? innerMap.getOrDefault(secondKey, 0.0) : 0.0;
    }

    private void sendSimilarityEvent(long firstKey, long secondKey, double minSum,
                                     double sumFirst, double sumSecond) {
        double similarity = minSum / (Math.sqrt(sumFirst) * Math.sqrt(sumSecond));

        EventSimilarityAvro avro = EventSimilarityAvro.newBuilder()
                .setEventA((int) firstKey)
                .setEventB((int) secondKey)
                .setScore(similarity)
                .setTimestamp(Instant.now())
                .build();

        client.getProducer().send(new ProducerRecord<>("stats.events-similarity.v1", avro));
        log.info("Отправлено сходство для пары ({}, {}): {}", firstKey, secondKey, similarity);
    }

    private double computeWeightActionType(ActionTypeAvro actionType) {
        return switch (actionType) {
            case VIEW -> 0.4;
            case REGISTER -> 0.8;
            case LIKE -> 1.0;
        };
    }

    private void closeResources() {
        try {
            client.getProducer().flush();
            client.getConsumer().commitSync();
        } finally {
            log.info("Закрываем консьюмер и продюсер");
            client.stop();
        }
    }
}