package com.team.GroundTruth.domain.dto.hazardreport;

import com.team.GroundTruth.domain.entity.Hazard.Hazard;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * DTO representing a hazard report.
 *
 * @param id report identifier
 * @param imageBytes image bytes for the report
 * @param createdAt report creation timestamp
 * @param hazards hazards detected in the report
 */
public record HazardReportDto(
        UUID id,
        byte[] imageBytes,
        Instant createdAt,
        List<Hazard> hazards
) {
}
