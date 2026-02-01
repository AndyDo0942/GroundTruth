package com.team.GroundTruth.domain.dto.routing;

/**
 * DTO representing a safety risk marker for map visualization.
 * Used for walk safe routes to show areas with low lighting, high crime, etc.
 *
 * @param latitude risk location latitude
 * @param longitude risk location longitude
 * @param riskType type of risk (e.g., "low_lighting", "high_crime", "low_density")
 * @param value the modifier value (0-1 scale)
 * @param severity computed severity level ("low", "medium", "high")
 * @param iconType suggested icon type for frontend rendering
 */
public record RiskMarkerDto(
		double latitude,
		double longitude,
		String riskType,
		double value,
		String severity,
		String iconType
) {
	/**
	 * Determines severity level based on risk type and value.
	 * For lighting/density: lower values = higher risk
	 * For crime: higher values = higher risk
	 */
	public static String severityFor(String riskType, double value) {
		if ("high_crime".equals(riskType)) {
			// Crime: higher is worse
			if (value >= 0.7) return "high";
			if (value >= 0.4) return "medium";
			return "low";
		} else {
			// Lighting/density: lower is worse
			if (value <= 0.3) return "high";
			if (value <= 0.6) return "medium";
			return "low";
		}
	}

	/**
	 * Determines the icon type based on risk type.
	 */
	public static String iconTypeFor(String riskType) {
		if (riskType == null) {
			return "warning";
		}
		return switch (riskType) {
			case "low_lighting" -> "dark";
			case "high_crime" -> "crime";
			case "low_density" -> "isolated";
			default -> "warning";
		};
	}
}