package com.team.GroundTruth.services.user_service.impl;

import com.team.GroundTruth.domain.CreateUserRequest;
import com.team.GroundTruth.domain.entity.HazardReport.HazardReport;
import com.team.GroundTruth.domain.entity.User.User;
import com.team.GroundTruth.repository.UserRepository;
import com.team.GroundTruth.services.user_service.UserService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    public UserServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }
    @Override
    public User createUser(CreateUserRequest request) {
        Instant now = Instant.now();

        User user = new User(
                null,
                request.username(),
                new ArrayList<HazardReport>()
        );

        return this.userRepository.save(user);

    }

}
