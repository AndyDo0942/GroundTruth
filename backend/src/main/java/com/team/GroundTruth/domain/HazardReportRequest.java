package com.team.GroundTruth.domain;

import java.util.UUID;

/**
 * Domain request used to create or update a hazard report.
 *
 * @param imageBytes image bytes for the report
 * @param imageContentType image MIME type
 * @param latitude latitude coordinate
 * @param longitude longitude coordinate
 */
public record HazardReportRequest(byte[] imageBytes, String imageContentType, float latitude, float longitude) {}
