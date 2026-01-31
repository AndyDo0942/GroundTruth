package com.team.GroundTruth.controller;


import com.team.GroundTruth.domain.UserRequest;
import com.team.GroundTruth.domain.dto.user.UserRequestDto;
import com.team.GroundTruth.domain.dto.user.UserDto;
import com.team.GroundTruth.domain.entity.User.User;
import com.team.GroundTruth.mapper.user_mapper.UserMapper;
import com.team.GroundTruth.services.user_service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for user-related endpoints.
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {
    /**
     * Service layer for user operations.
     */
    private final UserService userService;
    /**
     * Mapper between DTOs and domain objects.
     */
    private final UserMapper userMapper;

    /**
     * Creates a controller with required collaborators.
     *
     * @param userService service handling user operations
     * @param userMapper mapper between DTOs and domain models
     */
    public UserController(UserService userService, UserMapper userMapper) {
        this.userService = userService;
        this.userMapper = userMapper;
    }

    /**
     * Creates a new user from the supplied request payload.
     *
     * @param dto request payload containing the username
     * @return created user representation
     */
    @PostMapping
    public ResponseEntity<UserDto> createUser(@Valid @RequestBody UserRequestDto dto) {
        UserRequest userRequest = userMapper.fromDto(dto);

        User user = userService.createUser(userRequest);

        UserDto userDto = userMapper.toDto(user);

        return new ResponseEntity<>(userDto, HttpStatus.CREATED);


    }

    @GetMapping
    /**
     * Returns all users.
     *
     * @return list of users
     */
    public ResponseEntity<List<UserDto>> getAllUsers() {
        List<User> users = userService.getUsers();
        List<UserDto> userDtoList = users.stream().map(userMapper :: toDto).toList();
        return new ResponseEntity<>(userDtoList, HttpStatus.OK);
    }

    @PutMapping("/{userId}")
    /**
     * Updates a user by id.
     *
     * @param userId user identifier
     * @param dto request payload containing updated user data
     * @return updated user representation
     */
    public ResponseEntity<UserDto> updateUser(@PathVariable UUID userId, @Valid @RequestBody UserRequestDto dto) {
        UserRequest userRequest = userMapper.fromDto(dto);

        User updatedUser = userService.updateUser(userId, userRequest);

        UserDto userDto = userMapper.toDto(updatedUser);

        return new ResponseEntity<>(userDto, HttpStatus.OK);
    }

    @DeleteMapping("/{userId}")
    /**
     * Deletes a user by id.
     *
     * @param userId user identifier
     * @return no content on success
     */
    public ResponseEntity<UserDto> deleteUser(@PathVariable UUID userId) {

        userService.deleteUser(userId);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }
}
