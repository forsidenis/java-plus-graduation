package ru.practicum.category.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.practicum.common.exception.ConditionsNotMetException;

@Component
@Slf4j
public class EventClientFallback implements EventClient {
    @Override
    public Long countEventsByCategory(Long categoryId) {
        log.warn("EventClient fallback: невозможно получить количество событий для categoryId={}", categoryId);
        throw new ConditionsNotMetException("Не удалось проверить наличие событий для категории, удаление невозможно");
    }
}