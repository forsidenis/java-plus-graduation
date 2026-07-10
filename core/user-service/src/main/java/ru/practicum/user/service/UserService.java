package ru.practicum.user.service;

import ru.practicum.common.dto.NewUserRequest;
import ru.practicum.common.dto.UserDto;
import ru.practicum.common.dto.UserShortDto;

import java.util.List;

public interface UserService {
    UserDto saveUser(NewUserRequest request);

    List<UserDto> getUsers(List<Long> ids, Long from, Long size);

    void deleteUser(Integer id);

    UserShortDto getUserShort(Long userId);
}