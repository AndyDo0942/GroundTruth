package com.team.GroundTruth.Controller;


import com.team.GroundTruth.domain.CreateUserRequest;
import com.team.GroundTruth.domain.dto.user.CreateUserRequestDto;
import com.team.GroundTruth.domain.dto.user.UserDto;
import com.team.GroundTruth.domain.entity.User.User;
import com.team.GroundTruth.mapper.UserMapper;
import com.team.GroundTruth.services.user_service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {
    private final UserService userService;
    private final UserMapper userMapper;

    public UserController(UserService userService, UserMapper userMapper) {
        this.userService = userService;
        this.userMapper = userMapper;
    }

    @PostMapping
    public ResponseEntity<UserDto> createUser(@Valid @RequestBody CreateUserRequestDto dto) {
        CreateUserRequest createUserRequest = userMapper.fromDto(dto);

        User user = userService.createUser(createUserRequest);

        UserDto userDto = userMapper.toDto(user);

        return new ResponseEntity<>(userDto, HttpStatus.CREATED);


    }
}
