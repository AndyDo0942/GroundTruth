package com.team.GroundTruth.services.user_service;

import com.team.GroundTruth.domain.CreateUserRequest;
import com.team.GroundTruth.domain.entity.User.User;

import java.util.List;

public interface UserService {
    User createUser(CreateUserRequest createUserRequest);


}
