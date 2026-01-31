package com.team.GroundTruth.repository;

import com.team.GroundTruth.domain.entity.HazardReport.HazardReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Repository for persisting {@link com.team.GroundTruth.domain.entity.HazardReport.HazardReport} entities.
 */
public interface HazardReportRepository extends JpaRepository<HazardReport, UUID> {}
