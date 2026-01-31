package com.team.GroundTruth.entity.Hazard;


import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name="Hazards")
public class Hazard {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

}
