package com.team.GroundTruth.domain;

import java.util.UUID;

/**
 * Domain request used to create or update a hazard report.
 *
 * @param userId user identifier
 * @param imageUrl image URL for the report
 */
public record HazardReportRequest(UUID userId, String imageUrl) {}
