package ru.practicum.rating.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class RequestClientFallback implements RequestClient {
    @Override
    public boolean existsByEventAndUserAndStatusConfirmed(Long eventId, Long userId) {
        log.warn("RequestClient fallback: возвращаем false для eventId={}, userId={}", eventId, userId);
        return false;
    }
}