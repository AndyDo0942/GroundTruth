package com.team.GroundTruth.domain.dto.hazardreport;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import org.hibernate.validator.constraints.Length;

/**
 * DTO used to create or update a hazard report.
 *
 * @param userId user identifier
 * @param imageUrl image URL for the report
 */
public record HazardReportRequestDto(
        @NotNull(message = ERROR_MESSAGE_USER_ID)
        UUID userId,
        @NotBlank(message = ERROR_MESSAGE_IMAGE_URL)
        @Length(max = 2048, message = ERROR_MESSAGE_IMAGE_URL)
        String imageUrl
) {
    private static final String ERROR_MESSAGE_USER_ID = "Invalid user id";
    private static final String ERROR_MESSAGE_IMAGE_URL = "Invalid image url";
}
