package ru.practicum;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;
import ru.practicum.ewm.stats.avro.ActionTypeAvro;
import ru.practicum.ewm.stats.proto.collector.ActionTypeProto;
import ru.practicum.ewm.stats.proto.collector.Empty;
import ru.practicum.ewm.stats.proto.collector.UserActionProto;
import ru.practicum.ewm.stats.proto.collector.UserActionServiceGrpc;
import ru.practicum.ewm.stats.proto.dashboard.InteractionsCountRequestProto;
import ru.practicum.ewm.stats.proto.dashboard.RecommendedEventProto;
import ru.practicum.ewm.stats.proto.dashboard.RecommendationsControllerGrpc;
import ru.practicum.ewm.stats.proto.dashboard.SimilarEventsRequestProto;
import ru.practicum.ewm.stats.proto.dashboard.UserPredictionsRequestProto;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Component
@RequiredArgsConstructor
public class RecommendationGrpcClient {

    private static final Logger log = LoggerFactory.getLogger(RecommendationGrpcClient.class);

    @GrpcClient("collector")
    private UserActionServiceGrpc.UserActionServiceBlockingStub collectorStub;

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