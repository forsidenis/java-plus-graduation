package ru.practicum;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;
import ru.practicum.ewm.stats.avro.ActionTypeAvro;
import stats.service.collector.ActionTypeProto;
import stats.service.collector.UserActionControllerGrpc;
import stats.service.collector.UserActionProto;
import stats.service.dashboard.InteractionsCountRequestProto;
import stats.service.dashboard.RecommendedEventProto;
import stats.service.dashboard.RecommendationsControllerGrpc;
import stats.service.dashboard.SimilarEventsRequestProto;
import stats.service.dashboard.UserPredictionsRequestProto;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class RecommendationGrpcClient {

    @GrpcClient("collector")
    private UserActionControllerGrpc.UserActionControllerBlockingStub collectorStub;

    @GrpcClient("analyzer")
    private RecommendationsControllerGrpc.RecommendationsControllerBlockingStub analyzerStub;

    public void sendUserAction(long userId, long eventId, ActionTypeAvro actionType, long timestamp) {
        ActionTypeProto protoAction = mapAvroToProto(actionType);
        UserActionProto request = UserActionProto.newBuilder()
                .setUserId(userId)
                .setEventId(eventId)
                .setActionType(protoAction)
                .setTimestamp(com.google.protobuf.Timestamp.newBuilder()
                        .setSeconds(Instant.ofEpochMilli(timestamp).getEpochSecond())
                        .setNanos(Instant.ofEpochMilli(timestamp).getNano())
                        .build())
                .build();
        collectorStub.collectUserAction(request);
        log.debug("Отправлено действие пользователя: {}", request);
    }

    public List<RecommendedEventProto> getRecommendationsForUser(long userId, int maxResults) {
        UserPredictionsRequestProto request = UserPredictionsRequestProto.newBuilder()
                .setUserId(userId)
                .setMaxResults(maxResults)
                .build();
        Iterator<RecommendedEventProto> iterator = analyzerStub.getRecommendationsForUser(request);
        return toList(iterator);
    }

    public List<RecommendedEventProto> getSimilarEvents(long eventId, long userId, int maxResults) {
        SimilarEventsRequestProto request = SimilarEventsRequestProto.newBuilder()
                .setEventId(eventId)
                .setUserId(userId)
                .setMaxResults(maxResults)
                .build();
        Iterator<RecommendedEventProto> iterator = analyzerStub.getSimilarEvents(request);
        return toList(iterator);
    }

    public List<RecommendedEventProto> getInteractionsCount(List<Long> eventIds) {
        InteractionsCountRequestProto request = InteractionsCountRequestProto.newBuilder()
                .addAllEventId(eventIds)
                .build();
        Iterator<RecommendedEventProto> iterator = analyzerStub.getInteractionsCount(request);
        return toList(iterator);
    }

    private List<RecommendedEventProto> toList(Iterator<RecommendedEventProto> iterator) {
        List<RecommendedEventProto> list = new ArrayList<>();
        iterator.forEachRemaining(list::add);
        return list;
    }

    private ActionTypeProto mapAvroToProto(ActionTypeAvro avro) {
        return switch (avro) {
            case VIEW -> ActionTypeProto.ACTION_VIEW;
            case REGISTER -> ActionTypeProto.ACTION_REGISTER;
            case LIKE -> ActionTypeProto.ACTION_LIKE;
        };
    }
}