package ru.practicum.user.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.common.dto.NewUserRequest;
import ru.practicum.common.dto.UserDto;
import ru.practicum.common.dto.UserShortDto;
import ru.practicum.common.exception.AlreadyExistsException;
import ru.practicum.common.exception.NotFoundException;
import ru.practicum.user.mapper.UserMapper;
import ru.practicum.user.model.User;
import ru.practicum.user.repository.UserRepository;
import ru.practicum.user.service.UserService;

import java.util.List;

@Service
@Slf4j
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    private final UserRepository userRepository;

    @Override
    @Transactional
    public UserDto saveUser(NewUserRequest request) {
        log.info("Создание нового пользователя: {}", request);
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new AlreadyExistsException("Пользователь с адресом '" + request.getEmail() + "' уже существует");
        }
        User user = UserMapper.toEntity(request);
        user = userRepository.save(user);
        log.info("Создан пользователь с id={}", user.getId());
        return UserMapper.toDto(user);
    }

    @Override
    public List<UserDto> getUsers(List<Long> ids, Long from, Long size) {
        if (ids != null && !ids.isEmpty()) {
            log.info("Получение пользователей по ids: {}", ids);
            return userRepository.getUsers(ids).stream()
                    .map(UserMapper::toDto)
                    .toList();
        } else {
            log.info("Получение пользователей с from={}, size={}", from, size);
            return userRepository.getUsers(from, size).stream()
                    .map(UserMapper::toDto)
                    .toList();
        }
    }

    @Override
    @Transactional
    public void deleteUser(Integer id) {
        log.info("Удаление пользователя с ID: {}", id);
        if (!userRepository.existsById(id)) {
            throw new NotFoundException("Пользователь с id=" + id + " не найден");
        }
        userRepository.deleteById(id);
    }

    @Override
    public UserShortDto getUserShort(Long userId) {
        User user = userRepository.findById(userId.intValue())
                .orElseThrow(() -> new NotFoundException("Пользователь с id=" + userId + " не найден"));
        return UserMapper.toShortDto(user);
    }
}