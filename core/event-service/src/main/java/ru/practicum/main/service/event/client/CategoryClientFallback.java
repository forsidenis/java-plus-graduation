package ru.practicum.main.service.event.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.practicum.common.dto.CategoryDto;

@Component
@Slf4j
public class CategoryClientFallback implements CategoryClient {
    @Override
    public CategoryDto getCategory(Long catId) {
        log.warn("CategoryClient fallback: category-service unavailable, returning dummy category for catId={}", catId);
        return CategoryDto.builder()
                .id(catId)
                .name("dummy_category_" + catId)
                .build();
    }
}