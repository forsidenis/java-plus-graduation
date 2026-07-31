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
        log.info("Запуск AggregationStarter...");

        while (true) {
            try {
                client.getConsumer().subscribe(List.of("stats.user-actions.v1"));
                log.info("Подписка на топик stats.user-actions.v1 выполнена");
                break;
            } catch (Exception e) {
                log.warn("Ошибка при подключении к Kafka: {}", e.getMessage());
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }

        try {
            while (true) {
                ConsumerRecords<String, UserActionAvro> records;
                try {
                    records = client.getConsumer().poll(Duration.ofSeconds(1));
                } catch (Exception e) {
                    log.warn("Ошибка при опросе Kafka: {}", e.getMessage());
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                    continue;
                }

                for (ConsumerRecord<String, UserActionAvro> record : records) {
                    processUserAction(record.value());
                }
            }
        } catch (WakeupException ignored) {
        } catch (Exception e) {
            log.error("Критическая ошибка в цикле обработки", e);
        } finally {
            closeResources();
        }
    }

    private void processUserAction(UserActionAvro data) {
        log.debug("Обработка действия: {}", data);
        long eventId = data.getEventId();
        long userId = data.getUserId();

        double oldWeight = getUserWeight(eventId, userId);
        double newWeight = computeWeightActionType(data.getActionType());

        if (newWeight <= oldWeight) {
            log.debug("Новый вес {} не превышает старый {}, пересчет не требуется", newWeight, oldWeight);
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
        eventUserActionMatrix.computeIfAbsent(eventId, k -> new HashMap<>()).put(userId, newWeight);
        log.debug("Обновлен вес: event={}, user={}, weight={}", eventId, userId, newWeight);
    }

    private void updateEventSum(long eventId, double oldWeight, double newWeight) {
        double delta = newWeight - oldWeight;
        eventSumValue.merge(eventId, delta, Double::sum);
        log.debug("Обновлена сумма для event {}: {}", eventId, eventSumValue.get(eventId));
    }

    private void recalculateSimilarities(long eventId, long userId, double oldWeight, double newWeight) {
        for (long otherEventId : eventSumValue.keySet()) {
            if (otherEventId == eventId) continue;
            double otherUserWeight = getUserWeight(otherEventId, userId);
            if (otherUserWeight == 0.0) continue;

            long first = Math.min(eventId, otherEventId);
            long second = Math.max(eventId, otherEventId);

            double sumFirst = eventSumValue.getOrDefault(first, 0.0);
            double sumSecond = eventSumValue.getOrDefault(second, 0.0);
            if (sumFirst <= 0 || sumSecond <= 0) continue;

            double minOld = Math.min(oldWeight, otherUserWeight);
            double minNew = Math.min(newWeight, otherUserWeight);
            double deltaMin = minNew - minOld;

            double currentMinSum = minWeightsSums.computeIfAbsent(first, k -> new HashMap<>()).getOrDefault(second, 0.0);
            double updatedMinSum = currentMinSum + deltaMin;
            minWeightsSums.get(first).put(second, updatedMinSum);

            sendSimilarityEvent(first, second, updatedMinSum, sumFirst, sumSecond);
        }
    }

    private void sendSimilarityEvent(long first, long second, double minSum, double sumFirst, double sumSecond) {
        double similarity = minSum / (Math.sqrt(sumFirst) * Math.sqrt(sumSecond));
        EventSimilarityAvro avro = EventSimilarityAvro.newBuilder()
                .setEventA((int) first)
                .setEventB((int) second)
                .setScore(similarity)
                .setTimestamp(Instant.now())
                .build();
        try {
            client.getProducer().send(new ProducerRecord<>("stats.events-similarity.v1", avro));
            log.info("Отправлено сходство {} для пары ({}, {})", similarity, first, second);
        } catch (Exception e) {
            log.error("Ошибка отправки в Kafka", e);
        }
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
        } catch (Exception e) {
            log.warn("Ошибка при закрытии ресурсов", e);
        } finally {
            client.stop();
        }
    }
}