package ru.practicum.category.service.impl;

import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.category.client.EventClient;
import ru.practicum.category.mapper.CategoryMapper;
import ru.practicum.category.model.Category;
import ru.practicum.category.repository.CategoryRepository;
import ru.practicum.category.service.CategoryService;
import ru.practicum.common.dto.CategoryDto;
import ru.practicum.common.exception.AlreadyExistsException;
import ru.practicum.common.exception.ConditionsNotMetException;
import ru.practicum.common.exception.NotFoundException;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;
    private final EventClient eventClient;

    @Override
    @Transactional
    public CategoryDto createCategory(CategoryDto categoryDto) {
        log.info("Создание новой категории с именем: {}", categoryDto.getName());
        if (categoryRepository.existsByName(categoryDto.getName())) {
            throw new AlreadyExistsException("Категория с именем '" + categoryDto.getName() + "' уже существует");
        }
        Category category = CategoryMapper.toEntity(categoryDto);
        try {
            category = categoryRepository.save(category);
            log.info("Категория успешно создана с id: {}", category.getId());
            return CategoryMapper.toDto(category);
        } catch (DataIntegrityViolationException e) {
            throw new AlreadyExistsException("Категория с именем '" + categoryDto.getName() + "' уже существует");
        }
    }

    @Override
    @Transactional
    public CategoryDto updateCategory(Long catId, CategoryDto categoryDto) {
        log.info("Обновление категории с id: {}", catId);
        Category category = categoryRepository.findById(catId)
                .orElseThrow(() -> new NotFoundException("Категория с id=" + catId + " не найдена"));
        if (!category.getName().equals(categoryDto.getName()) &&
                categoryRepository.existsByName(categoryDto.getName())) {
            throw new AlreadyExistsException("Категория с именем '" + categoryDto.getName() + "' уже существует");
        }
        category.setName(categoryDto.getName());
        try {
            category = categoryRepository.save(category);
            log.info("Категория с id: {} успешно обновлена", catId);
            return CategoryMapper.toDto(category);
        } catch (DataIntegrityViolationException e) {
            throw new AlreadyExistsException("Категория с именем '" + categoryDto.getName() + "' уже существует");
        }
    }

    @Override
    @Transactional
    public void deleteCategory(Long catId) {
        log.info("Удаление категории с id: {}", catId);
        if (!categoryRepository.existsById(catId)) {
            throw new NotFoundException("Категория с id=" + catId + " не найдена");
        }
        try {
            Long eventsCount = eventClient.countEventsByCategory(catId);
            if (eventsCount != null && eventsCount > 0) {
                throw new ConditionsNotMetException(
                        "Невозможно удалить категорию с id=" + catId + ", так как она связана с существующими событиями"
                );
            }
        } catch (FeignException e) {
            throw new ConditionsNotMetException(
                    "Невозможно удалить категорию, так как не удалось проверить наличие связанных событий. Повторите попытку позже."
            );
        }
        try {
            categoryRepository.deleteById(catId);
            log.info("Категория с id: {} успешно удалена", catId);
        } catch (DataIntegrityViolationException e) {
            throw new ConditionsNotMetException(
                    "Невозможно удалить категорию с id=" + catId + ", так как она связана с существующими событиями"
            );
        }
    }

    @Override
    public List<CategoryDto> getCategories(int from, int size) {
        log.info("Получение списка категорий с from={}, size={}", from, size);
        int page = from / size;
        Pageable pageable = PageRequest.of(page, size);
        return categoryRepository.findAll(pageable)
                .stream()
                .map(CategoryMapper::toDto)
                .collect(Collectors.toList());
    }

    @Override
    public CategoryDto getCategory(Long catId) {
        log.info("Получение категории с id: {}", catId);
        Category category = categoryRepository.findById(catId)
                .orElseThrow(() -> new NotFoundException("Категория с id=" + catId + " не найдена"));
        return CategoryMapper.toDto(category);
    }
}