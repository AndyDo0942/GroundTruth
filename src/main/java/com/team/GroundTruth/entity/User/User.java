package com.team.GroundTruth.entity.User;


import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.UUID;

@Entity
@Table(name="users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, name="username", unique=true, length = 50)
    private String username;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<HazardReport> reports = new ArrayList<>();


}
