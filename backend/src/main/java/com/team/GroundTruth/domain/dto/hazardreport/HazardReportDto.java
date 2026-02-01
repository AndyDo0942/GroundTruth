package com.team.GroundTruth.domain.dto.hazardreport;

import com.team.GroundTruth.domain.entity.Hazard.Hazard;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * DTO representing a hazard report.
 *
 * @param id report identifier
 * @param userId user identifier
 * @param imageUrl image URL for the report
 * @param latitude report latitude
 * @param longitude report longitude
 * @param createdAt report creation timestamp
 * @param hazards hazards detected in the report
 */
public record HazardReportDto(
        UUID id,
        UUID userId,
        String imageUrl,
        Float latitude,
        Float longitude,
        Instant createdAt,
        List<Hazard> hazards
) {
}
