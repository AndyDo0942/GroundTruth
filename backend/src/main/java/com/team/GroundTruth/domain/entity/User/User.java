package com.team.GroundTruth.domain.entity.User;


import com.team.GroundTruth.domain.entity.HazardReport.HazardReport;
import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;
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

    public User() {}

    public User(UUID id, String username, List<HazardReport> reports) {
        this.id = id;
        this.username = username;
        this.reports = reports;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public List<HazardReport> getReports() {
        return reports;
    }

    public void setReports(List<HazardReport> reports) {
        this.reports = reports;
    }
}
