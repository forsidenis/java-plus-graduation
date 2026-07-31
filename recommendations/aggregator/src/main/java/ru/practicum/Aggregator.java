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
    public static void main(String[] args) throws InterruptedException {
        log.info("Запуск Aggregator");
        ConfigurableApplicationContext context = SpringApplication.run(Aggregator.class, args);
        Thread.sleep(5000);
        AggregationStarter aggregator = context.getBean(AggregationStarter.class);
        new Thread(aggregator::start).start();
        log.info("Aggregator запущен, регистрация в Eureka выполнена");
    }
}