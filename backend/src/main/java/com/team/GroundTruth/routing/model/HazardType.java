package com.team.GroundTruth.routing.model;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Hazard types identified by the Gemini analysis service.
 * Each type has a base cost multiplier that can be configured.
 */
public enum HazardType {
	/**
	 * Road pothole - affects driving.
	 */
	POTHOLE("pothole", TravelMode.DRIVE, 1.5),

	/**
	 * Ice on road/sidewalk - affects both driving and walking.
	 */
	ICE("ice", null, 2.0), // null = affects both modes

	/**
	 * Sidewalk cracks - affects walking accessibility.
	 */
	CRACKS("cracks", TravelMode.WALK, 1.3),

	/**
	 * Blocked sidewalk - affects walking accessibility.
	 */
	BLOCKED_SIDEWALK("blocked sidewalk", TravelMode.WALK, 3.0);

	private static final Map<String, HazardType> BY_LABEL = Arrays.stream(values())
			.collect(Collectors.toMap(
					h -> h.label.toLowerCase(),
					Function.identity()
			));

	private final String label;
	private final TravelMode affectedMode; // null means affects all modes
	private final double defaultBaseMultiplier;

	HazardType(String label, TravelMode affectedMode, double defaultBaseMultiplier) {
		this.label = label;
		this.affectedMode = affectedMode;
		this.defaultBaseMultiplier = defaultBaseMultiplier;
	}

	/**
	 * Returns the label as returned by Gemini (lowercase).
	 */
	public String getLabel() {
		return label;
	}

	/**
	 * Returns the travel mode this hazard affects, or null if it affects all modes.
	 */
	public TravelMode getAffectedMode() {
		return affectedMode;
	}

	/**
	 * Returns the default base multiplier for this hazard type.
	 */
	public double getDefaultBaseMultiplier() {
		return defaultBaseMultiplier;
	}

	/**
	 * Returns true if this hazard affects walking.
	 */
	public boolean affectsWalking() {
		return affectedMode == null || affectedMode == TravelMode.WALK;
	}

	/**
	 * Returns true if this hazard affects driving.
	 */
	public boolean affectsDriving() {
		return affectedMode == null || affectedMode == TravelMode.DRIVE;
	}

	/**
	 * Returns true if this is a walk accessibility hazard (Cracks, Blocked Sidewalk).
	 */
	public boolean isAccessibilityHazard() {
		return this == CRACKS || this == BLOCKED_SIDEWALK;
	}

	/**
	 * Finds a HazardType by its label (case-insensitive).
	 *
	 * @param label the hazard label from Gemini
	 * @return the matching HazardType, or null if not found
	 */
	public static HazardType fromLabel(String label) {
		if (label == null) {
			return null;
		}
		return BY_LABEL.get(label.toLowerCase().trim());
	}
}