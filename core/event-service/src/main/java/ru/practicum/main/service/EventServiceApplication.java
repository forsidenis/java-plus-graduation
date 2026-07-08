package ru.practicum.main.service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Import;
import ru.practicum.common.exception.ErrorHandler;
import ru.practicum.stat.client.StatsClient;

@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients
@Import({StatsClient.class, ErrorHandler.class})
public class EventServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(EventServiceApplication.class, args);
    }
}