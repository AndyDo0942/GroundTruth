package com.team.GroundTruth.routing.model;

/**
 * Route types that determine which cost strategy is used for pathfinding.
 */
public enum RouteType {
	/**
	 * Fastest route using base edge costs only.
	 * No safety or accessibility adjustments.
	 */
	FASTEST("fastest", TravelMode.WALK),

	/**
	 * Walking route optimized for safety.
	 * Uses walk_safe_edge_costs table (population density, streetlights, crime).
	 */
	WALK_SAFE("walk_safe", TravelMode.WALK),

	/**
	 * Walking route optimized for accessibility.
	 * Uses walk_accessibility_edge_costs table (cracks, blocked sidewalks).
	 */
	WALK_ACCESSIBLE("walk_accessible", TravelMode.WALK),

	/**
	 * Walking route combining safety and accessibility.
	 * Uses both walk_safe and walk_accessibility cost tables.
	 */
	WALK_SAFE_ACCESSIBLE("walk_safe_accessible", TravelMode.WALK),

	/**
	 * Fastest driving route using base edge costs.
	 */
	DRIVE_FASTEST("drive_fastest", TravelMode.DRIVE),

	/**
	 * Driving route avoiding hazards (potholes, ice).
	 * Uses drive_edge_costs table when available.
	 */
	DRIVE_SAFE("drive_safe", TravelMode.DRIVE);

	private final String value;
	private final TravelMode travelMode;

	RouteType(String value, TravelMode travelMode) {
		this.value = value;
		this.travelMode = travelMode;
	}

	/**
	 * Returns the string value for API usage.
	 *
	 * @return string value
	 */
	public String value() {
		return value;
	}

	/**
	 * Returns the underlying travel mode for edge filtering.
	 *
	 * @return travel mode
	 */
	public TravelMode travelMode() {
		return travelMode;
	}

	/**
	 * Returns true if this route type uses walk safety costs.
	 *
	 * @return true if uses walk safe costs
	 */
	public boolean usesWalkSafeCosts() {
		return this == WALK_SAFE || this == WALK_SAFE_ACCESSIBLE;
	}

	/**
	 * Returns true if this route type uses walk accessibility costs.
	 *
	 * @return true if uses walk accessibility costs
	 */
	public boolean usesWalkAccessibilityCosts() {
		return this == WALK_ACCESSIBLE || this == WALK_SAFE_ACCESSIBLE;
	}

	/**
	 * Returns true if this route type uses drive hazard costs.
	 *
	 * @return true if uses drive hazard costs
	 */
	public boolean usesDriveHazardCosts() {
		return this == DRIVE_SAFE;
	}

	/**
	 * Parses a route type from string value.
	 *
	 * @param value string value
	 * @return matching RouteType or null if not found
	 */
	public static RouteType fromValue(String value) {
		if (value == null) {
			return null;
		}
		for (RouteType type : values()) {
			if (type.value.equalsIgnoreCase(value)) {
				return type;
			}
		}
		return null;
	}
}