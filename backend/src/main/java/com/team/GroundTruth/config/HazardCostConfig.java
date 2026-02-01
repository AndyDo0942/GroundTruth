package com.team.GroundTruth.config;

import com.team.GroundTruth.routing.model.HazardType;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration for hazard cost multipliers.
 * Override defaults in application.properties:
 *
 * hazard.cost.multipliers.pothole=1.5
 * hazard.cost.multipliers.ice=2.0
 * hazard.cost.multipliers.cracks=1.3
 * hazard.cost.multipliers.blocked-sidewalk=3.0
 * hazard.cost.effect-radius-meters=50.0
 */
@Configuration
@ConfigurationProperties(prefix = "hazard.cost")
public class HazardCostConfig {

	private Map<String, Double> multipliers = new HashMap<>();
	private double effectRadiusMeters = 50.0;

	/**
	 * Returns the configured multipliers map.
	 */
	public Map<String, Double> getMultipliers() {
		return multipliers;
	}

	/**
	 * Sets the multipliers map from configuration.
	 */
	public void setMultipliers(Map<String, Double> multipliers) {
		this.multipliers = multipliers;
	}

	/**
	 * Returns the effect radius in meters for hazard impact on edges.
	 */
	public double getEffectRadiusMeters() {
		return effectRadiusMeters;
	}

	/**
	 * Sets the effect radius in meters.
	 */
	public void setEffectRadiusMeters(double effectRadiusMeters) {
		this.effectRadiusMeters = effectRadiusMeters;
	}

	/**
	 * Gets the multiplier for a specific hazard type.
	 * Falls back to the enum's default if not configured.
	 *
	 * @param hazardType the hazard type
	 * @return the configured or default multiplier
	 */
	public double getMultiplier(HazardType hazardType) {
		if (hazardType == null) {
			return 1.0;
		}

		// Check configured multipliers (support both formats: "cracks" and "blocked-sidewalk")
		String key = hazardType.getLabel().replace(" ", "-");
		Double configured = multipliers.get(key);
		if (configured != null) {
			return configured;
		}

		// Also try without hyphen
		configured = multipliers.get(hazardType.getLabel());
		if (configured != null) {
			return configured;
		}

		// Fall back to enum default
		return hazardType.getDefaultBaseMultiplier();
	}

	/**
	 * Computes the final cost multiplier for a hazard.
	 * Formula: base_multiplier * (1 + severity/100)
	 *
	 * Example with BLOCKED_SIDEWALK (base=3.0) and severity=80:
	 * 3.0 * (1 + 80/100) = 3.0 * 1.8 = 5.4
	 *
	 * @param hazardType the hazard type
	 * @param severity the severity score (0-100)
	 * @return the final multiplier
	 */
	public double computeMultiplier(HazardType hazardType, double severity) {
		return computeMultiplier(hazardType, severity, false);
	}

	/**
	 * Computes the final cost multiplier for a hazard, with optional deep-pothole adjustment.
	 * For POTHOLE, when hasDeepPothole is true (inference API found depth &gt; 5 cm), multiplies by 1.5.
	 *
	 * @param hazardType the hazard type
	 * @param severity the severity score (0-100)
	 * @param hasDeepPothole true if inference API reported any pothole deeper than 5 cm
	 * @return the final multiplier
	 */
	public double computeMultiplier(HazardType hazardType, double severity, boolean hasDeepPothole) {
		double baseMultiplier = getMultiplier(hazardType);
		double severityFactor = 1.0 + (Math.min(100, Math.max(0, severity)) / 100.0);
		double multiplier = baseMultiplier * severityFactor;
		if (hazardType == HazardType.POTHOLE && hasDeepPothole) {
			multiplier *= 1.5;
		}
		return multiplier;
	}
}