package com.team.GroundTruth.domain.dto.routing;

import java.util.UUID;

/**
 * DTO representing a hazard marker for map visualization.
 * Used for walk accessibility routes to show cracks, blocked sidewalks, etc.
 *
 * @param id hazard UUID
 * @param latitude hazard latitude
 * @param longitude hazard longitude
 * @param type hazard type (e.g., "cracks", "blocked sidewalk")
 * @param severity severity score (0-100) from Gemini analysis
 * @param iconType suggested icon type for frontend rendering
 */
public record HazardMarkerDto(
		UUID id,
		double latitude,
		double longitude,
		String type,
		double severity,
		String iconType
) {
	/**
	 * Determines the icon type based on hazard type.
	 */
	public static String iconTypeFor(String hazardType) {
		if (hazardType == null) {
			return "warning";
		}
		return switch (hazardType.toLowerCase()) {
			case "cracks" -> "crack";
			case "blocked sidewalk" -> "blocked";
			case "pothole" -> "pothole";
			case "ice" -> "ice";
			default -> "warning";
		};
	}
}