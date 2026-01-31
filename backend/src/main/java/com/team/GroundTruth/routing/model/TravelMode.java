package com.team.GroundTruth.routing.model;

/**
 * Supported travel modes for routing.
 */
public enum TravelMode {
	/**
	 * Walking mode.
	 */
	WALK("walk"),
	/**
	 * Driving mode.
	 */
	DRIVE("drive");

	private final String dbValue;

	TravelMode(String dbValue) {
		this.dbValue = dbValue;
	}

	/**
	 * Returns the database value used for filtering.
	 *
	 * @return database value
	 */
	public String dbValue() {
		return dbValue;
	}
}
