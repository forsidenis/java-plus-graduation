package ru.practicum.request.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.practicum.common.dto.UserShortDto;

@Component
@Slf4j
public class UserClientFallback implements UserClient {
    @Override
    public UserShortDto getUser(Long userId) {
        log.warn("UserClient fallback: user-service unavailable, returning dummy user for userId={}", userId);
        return UserShortDto.builder()
                .id(userId)
                .name("dummy_user_" + userId)
                .build();
    }
}