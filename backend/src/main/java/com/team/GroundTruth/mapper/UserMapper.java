package com.team.GroundTruth.mapper;

import com.team.GroundTruth.domain.CreateUserRequest;
import com.team.GroundTruth.domain.dto.user.CreateUserRequestDto;
import com.team.GroundTruth.domain.dto.user.UserDto;
import com.team.GroundTruth.domain.entity.User.User;

public interface UserMapper {
    CreateUserRequest fromDto(CreateUserRequestDto dto);
    UserDto toDto(User user);
}
