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
import ru.practicum.RecommendationGrpcClient;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class PublicCompilationServiceImpl implements PublicCompilationService {

    private final CompilationRepository compilationRepository;
    private final RecommendationGrpcClient recommendationGrpcClient;

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
    public Map<Long, Double> getRatingsForEvents(List<Event> events) {
        if (events == null || events.isEmpty()) return Map.of();
        List<Long> eventIds = events.stream().map(Event::getId).collect(Collectors.toList());
        try {
            var protoList = recommendationGrpcClient.getInteractionsCount(eventIds);
            return protoList.stream()
                    .collect(Collectors.toMap(
                            proto -> proto.getEventId(),
                            proto -> proto.getScore()
                    ));
        } catch (Exception e) {
            log.warn("Не удалось получить рейтинги для событий подборки: {}", e.getMessage());
            return Map.of();
        }
    }
}