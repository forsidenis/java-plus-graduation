package ru.practicum;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
public class AnalyzerApplication {
    public static void main(String[] args) {
        System.setProperty("spring.datasource.url", "jdbc:h2:mem:analyzer;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE");
        System.setProperty("spring.datasource.driver-class-name", "org.h2.Driver");
        System.setProperty("spring.datasource.username", "sa");
        System.setProperty("spring.datasource.password", "");
        System.setProperty("spring.jpa.database-platform", "org.hibernate.dialect.H2Dialect");
        System.setProperty("spring.jpa.hibernate.ddl-auto", "update");

        SpringApplication.run(AnalyzerApplication.class, args);
    }
}