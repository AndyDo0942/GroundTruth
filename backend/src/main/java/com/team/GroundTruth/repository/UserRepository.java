package com.team.GroundTruth.repository;

import com.team.GroundTruth.domain.entity.User.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {}
