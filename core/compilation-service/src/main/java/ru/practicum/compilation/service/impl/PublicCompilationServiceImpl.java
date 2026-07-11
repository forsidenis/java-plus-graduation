package ru.practicum.compilation.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import ru.practicum.compilation.model.Compilation;
import ru.practicum.compilation.repository.CompilationRepository;
import ru.practicum.compilation.service.PublicCompilationService;
import ru.practicum.exception.NotFoundException;
import ru.practicum.stat.client.StatsClient;
import ru.practicum.stat.dto.ViewStatsDto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class PublicCompilationServiceImpl implements PublicCompilationService {

    private final CompilationRepository compilationRepository;
    private final StatsClient statsClient;

    @Override
    public List<Compilation> getCompilations(Boolean pinned, int from, int size) {
        log.info("Получение подборок с параметрами: pinned={}, from={}, size={}", pinned, from, size);
        Pageable pageable = PageRequest.of(from / size, size);
        if (pinned != null) {
            return compilationRepository.findAllByPinned(pinned, pageable);
        }
        return compilationRepository.findAll(pageable).getContent();
    }

    @Override
    public Compilation getCompilationById(Long compId) {
        log.info("Получение подборки по id: {}", compId);
        return compilationRepository.findById(compId)
                .orElseThrow(() -> new NotFoundException("Подборка с id=" + compId + " не найдена"));
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
}