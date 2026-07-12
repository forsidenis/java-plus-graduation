package ru.practicum.event.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import ru.practicum.event.model.Compilation;
import ru.practicum.event.model.Event;
import ru.practicum.event.repository.CompilationRepository;
import ru.practicum.event.service.PublicCompilationService;
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
        Pageable pageable = PageRequest.of(from / size, size);
        if (pinned != null) {
            return compilationRepository.findAllByPinned(pinned, pageable);
        }
        return compilationRepository.findAll(pageable).getContent();
    }

    @Override
    public Compilation getCompilationById(Long compId) {
        return compilationRepository.findById(compId)
                .orElseThrow(() -> new NotFoundException("Подборка с id=" + compId + " не найдена"));
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
}