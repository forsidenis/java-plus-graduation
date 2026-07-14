package ru.practicum.event.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.dto.compilationDto.NewCompilationDto;
import ru.practicum.dto.compilationDto.UpdateCompilationRequest;
import ru.practicum.event.mapper.CompilationMapper;
import ru.practicum.event.model.Compilation;
import ru.practicum.event.model.Event;
import ru.practicum.event.repository.CompilationRepository;
import ru.practicum.event.repository.EventRepository;
import ru.practicum.event.service.AdminCompilationService;
import ru.practicum.exception.NotFoundException;
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
    private final EventRepository eventRepository;
    private final StatsClient statsClient;

    @Override
    @Transactional
    public Compilation createCompilation(NewCompilationDto dto) {
        log.info("Создание подборки: {}", dto);
        List<Event> events = getEventsByIds(dto.getEvents());
        Compilation compilation = CompilationMapper.INSTANCE.toEntity(dto, events);
        return compilationRepository.save(compilation);
    }

    @Override
    @Transactional
    public void deleteCompilation(Long compId) {
        Compilation compilation = findCompilationById(compId);
        compilationRepository.delete(compilation);
    }

    @Override
    @Transactional
    public Compilation updateCompilation(Long compId, UpdateCompilationRequest request) {
        Compilation compilation = findCompilationById(compId);
        List<Event> events = null;
        if (request.getEvents() != null) {
            events = getEventsByIds(request.getEvents());
        }
        CompilationMapper.INSTANCE.updateEntity(compilation, request, events);
        return compilationRepository.save(compilation);
    }

    @Override
    public Map<Long, Long> getViewsForEvents(List<Event> events) {
        if (events == null || events.isEmpty()) return Map.of();
        LocalDateTime earliest = events.stream()
                .map(e -> e.getPublishedOn() != null ? e.getPublishedOn() : e.getCreatedOn())
                .min(LocalDateTime::compareTo)
                .orElse(LocalDateTime.now().minusYears(10));
        List<String> uris = events.stream().map(e -> "/events/" + e.getId()).toList();
        List<ViewStatsDto> stats = statsClient.getStats(earliest, LocalDateTime.now(), uris, false);
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

    private List<Event> getEventsByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return Collections.emptyList();
        List<Event> events = eventRepository.findAllById(ids);
        if (events.size() != ids.size()) {
            List<Long> foundIds = events.stream().map(Event::getId).toList();
            List<Long> missing = ids.stream().filter(id -> !foundIds.contains(id)).toList();
            throw new NotFoundException("События с id " + missing + " не найдены");
        }
        return events;
    }
}