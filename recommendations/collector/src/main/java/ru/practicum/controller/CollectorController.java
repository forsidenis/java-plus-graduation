package ru.practicum.controller;

import com.google.protobuf.Empty;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import org.apache.avro.specific.SpecificRecordBase;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import ru.practicum.client.KafkaProducerConfig;
import ru.practicum.ewm.stats.avro.UserActionAvro;
import ru.practicum.mapper.UserActionMapper;
import stats.service.collector.UserActionControllerGrpc;
import stats.service.collector.UserActionProto;

import java.time.Duration;

@Slf4j
@GrpcService
public class CollectorController extends UserActionControllerGrpc.UserActionControllerImplBase implements AutoCloseable {

    private final KafkaProducerConfig kafkaProducerConfig;
    private Producer<String, SpecificRecordBase> producer;

    public CollectorController(KafkaProducerConfig kafkaProducerConfig) {
        this.kafkaProducerConfig = kafkaProducerConfig;

    }

    private synchronized Producer<String, SpecificRecordBase> getProducer() {
        if (producer == null) {
            try {
                producer = kafkaProducerConfig.createProducer();
                log.info("Kafka producer created successfully");
            } catch (Exception e) {
                log.error("Failed to create Kafka producer", e);
                throw new RuntimeException("Failed to create Kafka producer", e);
            }
        }
        return producer;
    }

    @Override
    public void collectUserAction(UserActionProto proto, StreamObserver<Empty> responseObserver) {
        log.info("Получены данные в proto: {}", proto);
        UserActionAvro avro = UserActionMapper.toAvro(proto);
        log.info("Маппинг данных в avro: {}", avro);
        try {
            getProducer().send(new ProducerRecord<>("stats.user-actions.v1", avro));
            responseObserver.onNext(Empty.getDefaultInstance());
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("Ошибка отправки в Kafka", e);
            responseObserver.onError(new StatusRuntimeException(Status.fromThrowable(e)));
        }
    }

    @Override
    public void close() {
        if (producer != null) {
            producer.flush();
            producer.close(Duration.ofSeconds(10));
        }
    }
}