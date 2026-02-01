package com.team.GroundTruth.domain.dto.hazardreport;

import jakarta.validation.constraints.NotNull;

/**
 * DTO used to carry hazard report metadata.
 */
public record HazardReportRequestDto(
        @NotNull(message = ERROR_MESSAGE_LATITUDE)
        Float latitude,
        @NotNull(message = ERROR_MESSAGE_LONGITUDE)
        Float longitude
) {
    private static final String ERROR_MESSAGE_LATITUDE = "Invalid latitude";
    private static final String ERROR_MESSAGE_LONGITUDE = "Invalid longitude";
}
