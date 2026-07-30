package ru.practicum.grpc;

import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.data.domain.PageRequest;
import ru.practicum.ewm.stats.proto.dashboard.InteractionsCountRequestProto;
import ru.practicum.ewm.stats.proto.dashboard.RecommendedEventProto;
import ru.practicum.ewm.stats.proto.dashboard.RecommendationsControllerGrpc;
import ru.practicum.ewm.stats.proto.dashboard.SimilarEventsRequestProto;
import ru.practicum.ewm.stats.proto.dashboard.UserPredictionsRequestProto;
import ru.practicum.model.EventSimilarity;
import ru.practicum.model.UserInteraction;
import ru.practicum.repository.EventSimilarityRepository;
import ru.practicum.repository.UserInteractionRepository;

import java.util.*;
import java.util.stream.Collectors;

@GrpcService
@Slf4j
@RequiredArgsConstructor
public class RecommendationsGrpcService extends RecommendationsControllerGrpc.RecommendationsControllerImplBase {

    private final EventSimilarityRepository similarityRepository;
    private final UserInteractionRepository interactionRepository;

    @Override
    public void getRecommendationsForUser(UserPredictionsRequestProto request,
                                          StreamObserver<RecommendedEventProto> responseObserver) {
        long userId = request.getUserId();
        int maxResults = request.getMaxResults();

        // 1. Получаем последние N взаимодействий пользователя (например, 10)
        List<UserInteraction> userInteractions = interactionRepository.findAllByUserId(userId);
        if (userInteractions.isEmpty()) {
            responseObserver.onCompleted();
            return;
        }

        // Сортируем по времени и берём последние 10 (или меньше)
        userInteractions.sort((a, b) -> Long.compare(b.getLastActionAt(), a.getLastActionAt()));
        int limit = Math.min(userInteractions.size(), 10);
        List<UserInteraction> recent = userInteractions.subList(0, limit);

        // Множество событий, с которыми пользователь уже взаимодействовал
        Set<Long> interactedEvents = userInteractions.stream()
                .map(UserInteraction::getEventId)
                .collect(Collectors.toSet());

        // Собираем кандидатов: для каждого из recent находим похожие события
        Map<Long, Double> candidateScores = new HashMap<>();
        for (UserInteraction interaction : recent) {
            long eventId = interaction.getEventId();
            List<EventSimilarity> similar = similarityRepository.findTopNByEventId(eventId,
                    PageRequest.of(0, maxResults * 2)); // побольше, чтобы отфильтровать
            for (EventSimilarity sim : similar) {
                long candidateId = (sim.getEventA() == eventId) ? sim.getEventB() : sim.getEventA();
                if (interactedEvents.contains(candidateId)) continue;
                // накапливаем сумму сходств (можно усреднить)
                candidateScores.merge(candidateId, sim.getScore(), Double::sum);
            }
        }

        // Сортируем по убыванию суммы сходств и выбираем top maxResults
        List<Long> sortedCandidates = candidateScores.entrySet().stream()
                .sorted((e1, e2) -> Double.compare(e2.getValue(), e1.getValue()))
                .limit(maxResults)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        // Для каждого кандидата вычисляем предсказанную оценку (используем взвешенную сумму)
        for (Long candidateId : sortedCandidates) {
            double predictedScore = predictScore(userId, candidateId);
            RecommendedEventProto response = RecommendedEventProto.newBuilder()
                    .setEventId(candidateId)
                    .setScore(predictedScore)
                    .build();
            responseObserver.onNext(response);
        }
        responseObserver.onCompleted();
    }

    private double predictScore(long userId, long candidateId) {
        // Находим K ближайших соседей (например, 5) из уже оценённых пользователем
        List<UserInteraction> userInteractions = interactionRepository.findAllByUserId(userId);
        Set<Long> interacted = userInteractions.stream()
                .map(UserInteraction::getEventId)
                .collect(Collectors.toSet());

        // Для каждого взаимодействия находим сходство с candidateId
        List<Double> similarities = new ArrayList<>();
        List<Double> weights = new ArrayList<>();
        for (Long interactedEvent : interacted) {
            EventSimilarity sim = similarityRepository.findByEventAAndEventB(
                    Math.min(interactedEvent, candidateId),
                    Math.max(interactedEvent, candidateId)
            ).orElse(null);
            if (sim != null) {
                similarities.add(sim.getScore());
                // вес пользователя для этого события
                UserInteraction ui = interactionRepository.findByUserIdAndEventId(userId, interactedEvent).orElseThrow();
                weights.add(ui.getWeight());
            }
        }

        if (similarities.isEmpty()) return 0.0;

        // Сортируем по убыванию сходства и берём первые K (5)
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < similarities.size(); i++) indices.add(i);
        indices.sort((i1, i2) -> Double.compare(similarities.get(i2), similarities.get(i1)));
        int k = Math.min(indices.size(), 5);
        double numerator = 0.0;
        double denominator = 0.0;
        for (int i = 0; i < k; i++) {
            int idx = indices.get(i);
            numerator += similarities.get(idx) * weights.get(idx);
            denominator += similarities.get(idx);
        }
        return denominator == 0 ? 0 : numerator / denominator;
    }

    @Override
    public void getSimilarEvents(SimilarEventsRequestProto request,
                                 StreamObserver<RecommendedEventProto> responseObserver) {
        long eventId = request.getEventId();
        long userId = request.getUserId();
        int maxResults = request.getMaxResults();

        // Получаем все сходства для eventId
        List<EventSimilarity> similarities = similarityRepository.findAllByEventId(eventId);
        // Сортируем по убыванию score
        similarities.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));

        // Получаем взаимодействия пользователя
        Set<Long> interacted = interactionRepository.findAllByUserId(userId).stream()
                .map(UserInteraction::getEventId)
                .collect(Collectors.toSet());

        int count = 0;
        for (EventSimilarity sim : similarities) {
            long otherId = (sim.getEventA() == eventId) ? sim.getEventB() : sim.getEventA();
            if (interacted.contains(otherId)) continue;
            if (count >= maxResults) break;
            RecommendedEventProto response = RecommendedEventProto.newBuilder()
                    .setEventId(otherId)
                    .setScore(sim.getScore())
                    .build();
            responseObserver.onNext(response);
            count++;
        }
        responseObserver.onCompleted();
    }

    @Override
    public void getInteractionsCount(InteractionsCountRequestProto request,
                                     StreamObserver<RecommendedEventProto> responseObserver) {
        List<Long> eventIds = request.getEventIdList();
        if (eventIds.isEmpty()) {
            responseObserver.onCompleted();
            return;
        }

        List<Object[]> results = interactionRepository.sumWeightsGroupedByEventIds(eventIds);
        Map<Long, Double> sumMap = results.stream()
                .collect(Collectors.toMap(
                        row -> (Long) row[0],
                        row -> (Double) row[1]
                ));

        for (Long eventId : eventIds) {
            double sum = sumMap.getOrDefault(eventId, 0.0);
            RecommendedEventProto response = RecommendedEventProto.newBuilder()
                    .setEventId(eventId)
                    .setScore(sum)
                    .build();
            responseObserver.onNext(response);
        }
        responseObserver.onCompleted();
    }
}