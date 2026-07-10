package ru.practicum.compilation.exception;

import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import ru.practicum.common.exception.ApiError;

@RestControllerAdvice
@Slf4j
public class FeignExceptionHandler {

    @ExceptionHandler(FeignException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ApiError handleFeignException(FeignException e) {
        log.error("Feign error in compilation-service: {}", e.getMessage(), e);
        return ApiError.builder()
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .reason("External service unavailable.")
                .message(e.getMessage())
                .build();
    }
}