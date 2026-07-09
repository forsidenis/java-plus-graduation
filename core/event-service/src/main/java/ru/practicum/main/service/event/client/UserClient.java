package ru.practicum.main.service.event.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import ru.practicum.common.dto.UserShortDto;

@FeignClient(name = "user-service", fallback = UserClientFallback.class)
public interface UserClient {
    @GetMapping("/internal/users/{userId}")
    UserShortDto getUser(@PathVariable("userId") Long userId);
}