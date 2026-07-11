package ru.practicum.service;

import ru.practicum.model.Compilation;

import java.util.List;
import java.util.Map;

public interface PublicCompilationService {

    List<Compilation> getCompilations(Boolean pinned, int from, int size);

    Compilation getCompilationById(Long compId);

    Map<Long, Long> getViewsForEvents(List<Long> eventIds);
}