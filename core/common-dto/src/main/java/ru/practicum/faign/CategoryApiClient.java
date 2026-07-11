package ru.practicum.faign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import ru.practicum.dto.categoryDto.CategoryDto;

import java.util.List;

@FeignClient(name = "category-service", contextId = "categoryApiClient", configuration = FeignConfig.class)
public interface CategoryApiClient {

    @GetMapping("/categories/{catId}")
    CategoryDto getCategory(@PathVariable Long catId);

    @GetMapping("/categories")
    List<CategoryDto> getCategories(@RequestParam(defaultValue = "0") int from,
                                    @RequestParam(defaultValue = "10") int size);
}