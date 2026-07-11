package ru.practicum.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.mapper.CompilationMapper;
import ru.practicum.model.Compilation;
import ru.practicum.repository.CompilationRepository;
import ru.practicum.service.AdminCompilationService;
import ru.practicum.dto.compilationDto.NewCompilationDto;
import ru.practicum.dto.compilationDto.UpdateCompilationRequest;
import ru.practicum.dto.eventDto.EventShortDto;
import ru.practicum.exception.NotFoundException;
import ru.practicum.faign.EventServiceFeign;
import ru.practicum.stat.client.StatsClient;
import ru.practicum.stat.dto.ViewStatsDto;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AdminCompilationServiceImpl implements AdminCompilationService {

    private final CompilationRepository compilationRepository;
    private final EventServiceFeign eventServiceFeign;
    private final StatsClient statsClient;

    @Override
    @Transactional
    public Compilation createCompilation(NewCompilationDto dto) {
        log.info("Создание подборки: {}", dto);
        List<Long> eventIds = getExistingEventIds(dto.getEvents());
        Compilation compilation = CompilationMapper.toEntity(dto, eventIds);
        compilation = compilationRepository.save(compilation);
        log.info("Подборка создана с id: {}", compilation.getId());
        return compilation;
    }

    @Override
    @Transactional
    public void deleteCompilation(Long compId) {
        log.info("Удаление подборки с id: {}", compId);
        findCompilationById(compId);
        compilationRepository.deleteById(compId);
        log.info("Подборка {} удалена", compId);
    }

    @Override
    @Transactional
    public Compilation updateCompilation(Long compId, UpdateCompilationRequest request) {
        log.info("Обновление подборки {} данными: {}", compId, request);
        Compilation compilation = findCompilationById(compId);
        List<Long> eventIds = null;
        if (request.getEvents() != null) {
            eventIds = getExistingEventIds(request.getEvents());
        }
        CompilationMapper.updateEntity(compilation, request, eventIds);
        compilation = compilationRepository.save(compilation);
        log.info("Подборка {} обновлена", compId);
        return compilation;
    }

    @Override
    public Map<Long, Long> getViewsForEvents(List<Long> eventIds) {
        if (eventIds == null || eventIds.isEmpty()) return Map.of();
        LocalDateTime start = LocalDateTime.now().minusYears(10);
        List<String> uris = eventIds.stream().map(id -> "/events/" + id).toList();
        List<ViewStatsDto> stats = statsClient.getStats(start, LocalDateTime.now(), uris, false);
        return stats.stream()
                .collect(Collectors.toMap(
                        v -> Long.parseLong(v.getUri().substring(v.getUri().lastIndexOf('/') + 1)),
                        ViewStatsDto::getHits,
                        (a, b) -> a
                ));
    }

    private Compilation findCompilationById(Long compId) {
        return compilationRepository.findById(compId)
                .orElseThrow(() -> new NotFoundException("Подборка с id=" + compId + " не найдена"));
    }

    private List<Long> getExistingEventIds(List<Long> eventIds) {
        if (eventIds == null || eventIds.isEmpty()) return Collections.emptyList();
        List<EventShortDto> events = eventServiceFeign.getEventsByIds(eventIds);
        if (events.size() != eventIds.size()) {
            List<Long> existing = events.stream().map(EventShortDto::getId).toList();
            List<Long> missing = eventIds.stream().filter(id -> !existing.contains(id)).toList();
            throw new NotFoundException("События с id " + missing + " не найдены");
        }
        return eventIds;
    }
}