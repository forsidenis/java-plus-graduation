package ru.practicum;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.ConfigurableApplicationContext;
import ru.practicum.controller.AggregationStarter;

@Slf4j
@SpringBootApplication
@EnableDiscoveryClient
public class Aggregator {
    public static void main(String[] args) {
        log.info("Запуск Aggregator Application");
        ConfigurableApplicationContext context = SpringApplication.run(Aggregator.class, args);

        AggregationStarter aggregator = context.getBean(AggregationStarter.class);
        log.info("Получен бин AggregationStarter, запускаем в отдельном потоке");
        new Thread(() -> {
            try {
                aggregator.start();
            } catch (Exception e) {
                log.error("Ошибка в AggregationStarter: ", e);
            }
        }).start();
    }
}