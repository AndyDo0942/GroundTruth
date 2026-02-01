package com.team.GroundTruth.routing.service;

import java.util.UUID;

/**
 * Service for managing walk accessibility edge costs based on hazards like Cracks and Blocked Sidewalk.
 */
public interface WalkAccessibilityService {

	/**
	 * Initializes walk accessibility edge costs by processing all accessibility hazards.
	 * Finds hazards with labels "Cracks" or "Blocked Sidewalk", locates nearby walk edges,
	 * and computes costs based on hazard type multiplier and severity.
	 *
	 * @return number of edges updated
	 */
	int initializeFromHazards();

	/**
	 * Recalculates costs for edges affected by a specific hazard.
	 * Call this when a hazard is added or updated.
	 *
	 * @param hazardId the hazard UUID that changed
	 * @param hazardLabel the hazard type label (e.g., "Cracks", "Blocked Sidewalk")
	 * @param lat hazard latitude
	 * @param lon hazard longitude
	 * @param severity hazard severity (0-100) from Gemini analysis
	 * @return number of edges updated
	 */
	int updateForHazard(UUID hazardId, String hazardLabel, double lat, double lon, double severity);

	/**
	 * Recalculates costs for edges affected by a pothole hazard, using inference depth when available.
	 *
	 * @param hazardId the hazard UUID
	 * @param hazardLabel the hazard type label (e.g. "pothole")
	 * @param lat hazard latitude
	 * @param lon hazard longitude
	 * @param severity hazard severity (0-100) from Gemini analysis
	 * @param hasDeepPothole true if inference API found any pothole deeper than 5 cm (applies 1.5x multiplier)
	 * @return number of edges updated
	 */
	int updateForPotholeHazard(UUID hazardId, String hazardLabel, double lat, double lon, double severity, boolean hasDeepPothole);

	/**
	 * Removes a hazard's contribution from affected edges and recalculates costs.
	 *
	 * @param hazardId the hazard UUID to remove
	 * @return number of edges updated
	 */
	int removeHazard(UUID hazardId);
}