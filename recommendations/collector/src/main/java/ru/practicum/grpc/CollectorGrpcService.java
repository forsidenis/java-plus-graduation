package ru.practicum.grpc;

import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.kafka.core.KafkaTemplate;
import ru.practicum.ewm.stats.avro.ActionTypeAvro;
import ru.practicum.ewm.stats.avro.UserActionAvro;
import ru.practicum.ewm.stats.proto.collector.ActionTypeProto;
import ru.practicum.ewm.stats.proto.collector.Empty;
import ru.practicum.ewm.stats.proto.collector.UserActionProto;
import ru.practicum.ewm.stats.proto.collector.UserActionServiceGrpc;

import java.time.Instant;

@GrpcService
@Slf4j
@RequiredArgsConstructor
public class CollectorGrpcService extends UserActionServiceGrpc.UserActionServiceImplBase {

    private final KafkaTemplate<Long, UserActionAvro> kafkaTemplate;
    private static final String TOPIC = "stats.user-actions.v1";

    @Override
    public void collectUserAction(UserActionProto request, StreamObserver<Empty> responseObserver) {
        log.info("Received gRPC user action: userId={}, eventId={}, action={}",
                request.getUserId(), request.getEventId(), request.getActionType());

        ActionTypeAvro actionType = mapActionType(request.getActionType());

        // Правильное создание Instant из секунд и наносекунд
        Instant timestamp = Instant.ofEpochSecond(
                request.getTimestamp().getSeconds(),
                request.getTimestamp().getNanos()
        );

        UserActionAvro avroMessage = UserActionAvro.newBuilder()
                .setUserId(request.getUserId())
                .setEventId(request.getEventId())
                .setActionType(actionType)
                .setTimestamp(timestamp)
                .build();

        kafkaTemplate.send(TOPIC, request.getUserId(), avroMessage);

        responseObserver.onNext(Empty.newBuilder().build());
        responseObserver.onCompleted();
    }

    private ActionTypeAvro mapActionType(ActionTypeProto proto) {
        return switch (proto) {
            case ACTION_VIEW -> ActionTypeAvro.VIEW;
            case ACTION_REGISTER -> ActionTypeAvro.REGISTER;
            case ACTION_LIKE -> ActionTypeAvro.LIKE;
            default -> throw new IllegalArgumentException("Unknown action type: " + proto);
        };
    }
}