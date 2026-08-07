package ru.practicum;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Import;
import ru.practicum.exception.ErrorHandler;

@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "ru.practicum.feign")
@Import(ErrorHandler.class)
public class RequestService {
    public static void main(String[] args) {
        SpringApplication.run(RequestService.class, args);
    }
}