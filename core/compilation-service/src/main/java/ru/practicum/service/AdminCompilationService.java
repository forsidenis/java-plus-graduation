package ru.practicum.service;

import ru.practicum.model.Compilation;
import ru.practicum.dto.compilationDto.NewCompilationDto;
import ru.practicum.dto.compilationDto.UpdateCompilationRequest;

import java.util.List;
import java.util.Map;

public interface AdminCompilationService {

    Compilation createCompilation(NewCompilationDto newCompilationDto);

    void deleteCompilation(Long compId);

    Compilation updateCompilation(Long compId, UpdateCompilationRequest request);

    Map<Long, Long> getViewsForEvents(List<Long> eventIds);
}