package ru.practicum.compilation.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.practicum.common.dto.ParticipationRequestDto;
import ru.practicum.common.dto.RequestStatus;

import java.util.Collections;
import java.util.List;

@Component
@Slf4j
public class RequestClientFallback implements RequestClient {
    @Override
    public List<ParticipationRequestDto> getAllByEventIdInAndStatus(List<Long> eventIds, RequestStatus status) {
        log.warn("RequestClient fallback: возвращаем пустой список для eventIds={}, status={}", eventIds, status);
        return Collections.emptyList();
    }
}