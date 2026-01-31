package com.team.GroundTruth.entity.HazardReport;

import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "hazard_reports")
public class HazardReport {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
}
