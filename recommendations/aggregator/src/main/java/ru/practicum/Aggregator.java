package ru.practicum;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import ru.practicum.controller.AggregationStarter;

@Slf4j
@SpringBootApplication
public class Aggregator {
    public static void main(String[] args) {
        log.info("Запуск Aggregator");
        ConfigurableApplicationContext context = SpringApplication.run(Aggregator.class, args);
        AggregationStarter aggregator = context.getBean(AggregationStarter.class);
        new Thread(aggregator::start).start();
        log.info("Aggregator запущен, регистрация в Eureka продолжается");
    }
}