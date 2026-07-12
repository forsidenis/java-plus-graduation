package ru.practicum.event.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.dto.categoryDto.CategoryDto;
import ru.practicum.event.mapper.CategoryMapper;
import ru.practicum.event.model.Category;
import ru.practicum.event.repository.CategoryRepository;
import ru.practicum.event.service.CategoryService;
import ru.practicum.exception.AlreadyExistsException;
import ru.practicum.exception.ConditionsNotMetException;
import ru.practicum.exception.NotFoundException;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;

    @Override
    @Transactional
    public CategoryDto createCategory(CategoryDto dto) {
        log.info("Создание категории: {}", dto.getName());
        if (categoryRepository.existsByName(dto.getName())) {
            throw new AlreadyExistsException("Категория с именем '" + dto.getName() + "' уже существует");
        }
        Category category = CategoryMapper.toEntity(dto);
        try {
            category = categoryRepository.save(category);
            return CategoryMapper.toDto(category);
        } catch (DataIntegrityViolationException e) {
            throw new AlreadyExistsException("Категория с именем '" + dto.getName() + "' уже существует");
        }
    }

    @Override
    @Transactional
    public CategoryDto updateCategory(Long catId, CategoryDto dto) {
        Category category = categoryRepository.findById(catId)
                .orElseThrow(() -> new NotFoundException("Категория с id=" + catId + " не найдена"));
        if (!category.getName().equals(dto.getName()) && categoryRepository.existsByName(dto.getName())) {
            throw new AlreadyExistsException("Категория с именем '" + dto.getName() + "' уже существует");
        }
        category.setName(dto.getName());
        try {
            category = categoryRepository.save(category);
            return CategoryMapper.toDto(category);
        } catch (DataIntegrityViolationException e) {
            throw new AlreadyExistsException("Категория с именем '" + dto.getName() + "' уже существует");
        }
    }

    @Override
    @Transactional
    public void deleteCategory(Long catId) {
        if (!categoryRepository.existsById(catId)) {
            throw new NotFoundException("Категория с id=" + catId + " не найдена");
        }
        try {
            categoryRepository.deleteById(catId);
        } catch (DataIntegrityViolationException e) {
            throw new ConditionsNotMetException("Невозможно удалить категорию, так как она связана с событиями");
        }
    }

    @Override
    public List<CategoryDto> getCategories(int from, int size) {
        Pageable pageable = PageRequest.of(from / size, size);
        return categoryRepository.findAll(pageable)
                .stream()
                .map(CategoryMapper::toDto)
                .collect(Collectors.toList());
    }

    @Override
    public CategoryDto getCategory(Long catId) {
        Category category = categoryRepository.findById(catId)
                .orElseThrow(() -> new NotFoundException("Категория с id=" + catId + " не найдена"));
        return CategoryMapper.toDto(category);
    }
}