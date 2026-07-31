package ru.practicum.client.impl;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.specific.SpecificRecordBase;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.springframework.stereotype.Component;
import ru.practicum.client.ClientConfiguration;
import ru.practicum.ewm.stats.avro.UserActionAvro;

import java.util.Properties;

@Slf4j
@Getter
@Setter
@Component
@RequiredArgsConstructor
public class KafkaClientConfigurationImpl implements ClientConfiguration {
    private final KafkaProperties kafkaProperties;
    private Consumer<String, UserActionAvro> consumer;
    private Producer<String, SpecificRecordBase> producer;

    @Override
    public Consumer<String, UserActionAvro> getConsumer() {
        if (consumer == null) {
            log.info("Инициализация Kafka Consumer");
            initConsumer();
        }
        return consumer;
    }

    @Override
    public Producer<String, SpecificRecordBase> getProducer() {
        if (producer == null) {
            log.info("Инициализация Kafka Producer");
            initProducer();
        }
        return producer;
    }

    @Override
    public void stop() {
        if (consumer != null) {
            try {
                consumer.close();
            } catch (Exception e) {
                log.warn("Ошибка при закрытии consumer", e);
            }
        }
        if (producer != null) {
            try {
                producer.close();
            } catch (Exception e) {
                log.warn("Ошибка при закрытии producer", e);
            }
        }
    }

    private void initConsumer() {
        Properties config = new Properties();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaProperties.getBootstrapServers());
        config.put(ConsumerConfig.GROUP_ID_CONFIG, kafkaProperties.getConsumer().getGroupId());
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, kafkaProperties.getConsumer().getKeyDeserializer());
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, kafkaProperties.getConsumer().getValueDeserializer());
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, kafkaProperties.getConsumer().getAutoOffsetReset());
        config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        consumer = new KafkaConsumer<>(config);
        log.info("Kafka Consumer создан");
    }

    private void initProducer() {
        Properties config = new Properties();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaProperties.getBootstrapServers());
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, kafkaProperties.getProducer().getKeySerializer());
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, kafkaProperties.getProducer().getValueSerializer());
        config.put(ProducerConfig.ACKS_CONFIG, kafkaProperties.getProducer().getAcks());
        config.put(ProducerConfig.RETRIES_CONFIG, kafkaProperties.getProducer().getRetries());
        config.put(ProducerConfig.LINGER_MS_CONFIG, kafkaProperties.getProducer().getLingerMs());
        config.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 1);
        config.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 120000);
        config.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 30000);
        config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        producer = new KafkaProducer<>(config);
        log.info("Kafka Producer создан");
    }
}