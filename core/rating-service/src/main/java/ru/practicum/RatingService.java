package ru.practicum;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import ru.practicum.config.FeignClientConfig;
import ru.practicum.exception.ErrorHandler;

@SpringBootApplication
@ComponentScan(basePackages = "ru.practicum") 
@Import({ErrorHandler.class, FeignClientConfig.class})
public class RatingService {
    public static void main(String[] args) {
        SpringApplication.run(RatingService.class, args);
    }
}