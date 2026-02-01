package com.team.GroundTruth.routing.service;

import com.team.GroundTruth.config.HazardCostConfig;
import com.team.GroundTruth.domain.entity.Hazard.Hazard;
import com.team.GroundTruth.entity.maps.EdgeEntity;
import com.team.GroundTruth.entity.maps.WalkAccessibilityEdgeCostEntity;
import com.team.GroundTruth.repository.HazardRepository;
import com.team.GroundTruth.routing.model.HazardType;
import com.team.GroundTruth.routing.repo.EdgeRepository;
import com.team.GroundTruth.routing.repo.WalkAccessibilityEdgeCostRepository;
import com.team.GroundTruth.routing.repo.WalkSafeModifierRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Implementation of WalkAccessibilityService for managing edge costs
 * based on accessibility hazards (Cracks, Blocked Sidewalk).
 */
@Service
@Transactional
public class WalkAccessibilityServiceImpl implements WalkAccessibilityService {

	private static final Logger LOG = LoggerFactory.getLogger(WalkAccessibilityServiceImpl.class);

	private final HazardRepository hazardRepository;
	private final EdgeRepository edgeRepository;
	private final WalkAccessibilityEdgeCostRepository costRepository;
	private final WalkSafeModifierRepository modifierRepository;
	private final HazardCostConfig hazardCostConfig;

	public WalkAccessibilityServiceImpl(
			HazardRepository hazardRepository,
			EdgeRepository edgeRepository,
			WalkAccessibilityEdgeCostRepository costRepository,
			WalkSafeModifierRepository modifierRepository,
			HazardCostConfig hazardCostConfig
	) {
		this.hazardRepository = Objects.requireNonNull(hazardRepository, "hazardRepository");
		this.edgeRepository = Objects.requireNonNull(edgeRepository, "edgeRepository");
		this.costRepository = Objects.requireNonNull(costRepository, "costRepository");
		this.modifierRepository = Objects.requireNonNull(modifierRepository, "modifierRepository");
		this.hazardCostConfig = Objects.requireNonNull(hazardCostConfig, "hazardCostConfig");
	}

	@Override
	public int initializeFromHazards() {
		List<Hazard> accessibilityHazards = hazardRepository.findAccessibilityHazardsWithLocation();
		LOG.info("Found {} accessibility hazards to process", accessibilityHazards.size());

		if (accessibilityHazards.isEmpty()) {
			return 0;
		}

		double radiusMeters = hazardCostConfig.getEffectRadiusMeters();

		// Group hazards by affected edges
		// Map: edgeId -> list of (hazardId, type, severity)
		Map<Long, List<HazardContribution>> edgeHazardMap = new HashMap<>();

		for (Hazard hazard : accessibilityHazards) {
			Float lat = hazard.getReport().getLatitude();
			Float lon = hazard.getReport().getLongitude();

			if (lat == null || lon == null) {
				continue;
			}

			HazardType hazardType = HazardType.fromLabel(hazard.getLabel());
			if (hazardType == null || !hazardType.isAccessibilityHazard()) {
				LOG.debug("Skipping non-accessibility hazard: {}", hazard.getLabel());
				continue;
			}

			List<Long> nearbyEdgeIds = modifierRepository.findWalkEdgeIdsNearPoint(
					lon.doubleValue(), lat.doubleValue(), radiusMeters);

			Double severity = hazard.getConfidence();
			if (severity == null) {
				severity = 50.0; // Default mid-severity
			}

			for (Long edgeId : nearbyEdgeIds) {
				edgeHazardMap.computeIfAbsent(edgeId, k -> new ArrayList<>())
						.add(new HazardContribution(hazard.getId(), hazardType, severity));
			}
		}

		if (edgeHazardMap.isEmpty()) {
			LOG.info("No edges found near accessibility hazards");
			return 0;
		}

		// Load base costs for affected edges
		long[] edgeIds = edgeHazardMap.keySet().stream().mapToLong(Long::longValue).toArray();
		Map<Long, EdgeEntity> edgesById = edgeRepository.findAllById(
				Arrays.stream(edgeIds).boxed().toList()
		).stream().collect(Collectors.toMap(EdgeEntity::getId, e -> e));

		// Load existing costs
		Map<Long, WalkAccessibilityEdgeCostEntity> existingCosts = costRepository.findByEdgeIds(edgeIds).stream()
				.collect(Collectors.toMap(WalkAccessibilityEdgeCostEntity::getEdgeId, c -> c));

		// Compute and save costs
		List<WalkAccessibilityEdgeCostEntity> toSave = new ArrayList<>();

		for (Map.Entry<Long, List<HazardContribution>> entry : edgeHazardMap.entrySet()) {
			Long edgeId = entry.getKey();
			List<HazardContribution> contributions = entry.getValue();

			EdgeEntity edge = edgesById.get(edgeId);
			if (edge == null) {
				continue;
			}

			double costSeconds = computeCost(edge.getCostSeconds(), contributions);
			UUID[] hazardIds = contributions.stream()
					.map(HazardContribution::hazardId)
					.toArray(UUID[]::new);

			WalkAccessibilityEdgeCostEntity costEntity = existingCosts.get(edgeId);
			if (costEntity == null) {
				costEntity = new WalkAccessibilityEdgeCostEntity(edgeId, costSeconds, hazardIds);
			} else {
				costEntity.setCostSeconds(costSeconds);
				costEntity.setContributingHazardIds(hazardIds);
			}
			toSave.add(costEntity);
		}

		costRepository.saveAll(toSave);
		LOG.info("Updated accessibility costs for {} edges", toSave.size());
		return toSave.size();
	}

	@Override
	public int updateForHazard(UUID hazardId, String hazardLabel, double lat, double lon, double severity) {
		HazardType hazardType = HazardType.fromLabel(hazardLabel);
		if (hazardType == null || !hazardType.isAccessibilityHazard()) {
			LOG.debug("Skipping non-accessibility hazard: {}", hazardLabel);
			return 0;
		}

		double radiusMeters = hazardCostConfig.getEffectRadiusMeters();
		List<Long> nearbyEdgeIds = modifierRepository.findWalkEdgeIdsNearPoint(lon, lat, radiusMeters);

		if (nearbyEdgeIds.isEmpty()) {
			return 0;
		}

		long[] edgeIds = nearbyEdgeIds.stream().mapToLong(Long::longValue).toArray();
		Map<Long, EdgeEntity> edgesById = edgeRepository.findAllById(
				nearbyEdgeIds
		).stream().collect(Collectors.toMap(EdgeEntity::getId, e -> e));

		Map<Long, WalkAccessibilityEdgeCostEntity> existingCosts = costRepository.findByEdgeIds(edgeIds).stream()
				.collect(Collectors.toMap(WalkAccessibilityEdgeCostEntity::getEdgeId, c -> c));

		// Get existing hazard details for recomputation
		Set<UUID> allExistingHazardIds = existingCosts.values().stream()
				.flatMap(c -> Arrays.stream(c.getContributingHazardIds()))
				.filter(id -> !id.equals(hazardId))
				.collect(Collectors.toSet());
		Map<UUID, Hazard> existingHazardsById = hazardRepository.findAllById(allExistingHazardIds).stream()
				.collect(Collectors.toMap(Hazard::getId, h -> h));

		List<WalkAccessibilityEdgeCostEntity> toSave = new ArrayList<>();

		for (Long edgeId : nearbyEdgeIds) {
			EdgeEntity edge = edgesById.get(edgeId);
			if (edge == null) {
				continue;
			}

			WalkAccessibilityEdgeCostEntity costEntity = existingCosts.get(edgeId);
			List<HazardContribution> contributions = new ArrayList<>();

			if (costEntity != null) {
				// Rebuild contributions from existing hazards (excluding the one being updated)
				for (UUID existingId : costEntity.getContributingHazardIds()) {
					if (!existingId.equals(hazardId)) {
						Hazard existingHazard = existingHazardsById.get(existingId);
						if (existingHazard != null) {
							HazardType existingType = HazardType.fromLabel(existingHazard.getLabel());
							Double existingSeverity = existingHazard.getConfidence();
							if (existingType != null && existingSeverity != null) {
								contributions.add(new HazardContribution(existingId, existingType, existingSeverity));
							}
						}
					}
				}
			}

			// Add the new/updated hazard contribution
			contributions.add(new HazardContribution(hazardId, hazardType, severity));

			UUID[] hazardIds = contributions.stream()
					.map(HazardContribution::hazardId)
					.toArray(UUID[]::new);

			double newCost = computeCost(edge.getCostSeconds(), contributions);

			if (costEntity != null) {
				costEntity.setCostSeconds(newCost);
				costEntity.setContributingHazardIds(hazardIds);
			} else {
				costEntity = new WalkAccessibilityEdgeCostEntity(edgeId, newCost, hazardIds);
			}
			toSave.add(costEntity);
		}

		costRepository.saveAll(toSave);
		LOG.info("Updated accessibility costs for {} edges due to hazard {}", toSave.size(), hazardId);
		return toSave.size();
	}

	@Override
	public int removeHazard(UUID hazardId) {
		List<WalkAccessibilityEdgeCostEntity> affectedEdges = costRepository.findByContributingHazardId(hazardId);

		if (affectedEdges.isEmpty()) {
			return 0;
		}

		long[] edgeIds = affectedEdges.stream()
				.mapToLong(WalkAccessibilityEdgeCostEntity::getEdgeId)
				.toArray();

		Map<Long, EdgeEntity> edgesById = edgeRepository.findAllById(
				Arrays.stream(edgeIds).boxed().toList()
		).stream().collect(Collectors.toMap(EdgeEntity::getId, e -> e));

		// Collect all remaining hazard IDs to look up their details
		Set<UUID> remainingHazardIds = affectedEdges.stream()
				.flatMap(c -> Arrays.stream(c.getContributingHazardIds()))
				.filter(id -> !id.equals(hazardId))
				.collect(Collectors.toSet());
		Map<UUID, Hazard> hazardsById = hazardRepository.findAllById(remainingHazardIds).stream()
				.collect(Collectors.toMap(Hazard::getId, h -> h));

		List<WalkAccessibilityEdgeCostEntity> toSave = new ArrayList<>();
		List<WalkAccessibilityEdgeCostEntity> toDelete = new ArrayList<>();

		for (WalkAccessibilityEdgeCostEntity costEntity : affectedEdges) {
			UUID[] remaining = Arrays.stream(costEntity.getContributingHazardIds())
					.filter(id -> !id.equals(hazardId))
					.toArray(UUID[]::new);

			if (remaining.length == 0) {
				toDelete.add(costEntity);
			} else {
				EdgeEntity edge = edgesById.get(costEntity.getEdgeId());
				if (edge != null) {
					// Recompute with remaining hazards using actual hazard data
					List<HazardContribution> contributions = new ArrayList<>();
					for (UUID id : remaining) {
						Hazard hazard = hazardsById.get(id);
						if (hazard != null) {
							HazardType type = HazardType.fromLabel(hazard.getLabel());
							Double severity = hazard.getConfidence();
							if (type != null && severity != null) {
								contributions.add(new HazardContribution(id, type, severity));
							}
						}
					}

					if (contributions.isEmpty()) {
						// No valid contributions remain, delete the cost record
						toDelete.add(costEntity);
					} else {
						UUID[] validHazardIds = contributions.stream()
								.map(HazardContribution::hazardId)
								.toArray(UUID[]::new);
						costEntity.setCostSeconds(computeCost(edge.getCostSeconds(), contributions));
						costEntity.setContributingHazardIds(validHazardIds);
						toSave.add(costEntity);
					}
				}
			}
		}

		costRepository.deleteAll(toDelete);
		costRepository.saveAll(toSave);

		LOG.info("Removed hazard {}: deleted {} cost records, updated {} cost records",
				hazardId, toDelete.size(), toSave.size());
		return affectedEdges.size();
	}

	/**
	 * Computes the accessibility-adjusted cost for an edge based on contributing hazards.
	 * Uses configurable base multipliers per hazard type and severity scores.
	 *
	 * Formula per hazard: base_multiplier[type] * (1 + severity/100)
	 * Multiple hazards: multipliers are combined multiplicatively
	 *
	 * Example:
	 * - CRACKS (base=1.3) with severity=60: 1.3 * 1.6 = 2.08
	 * - BLOCKED_SIDEWALK (base=3.0) with severity=80: 3.0 * 1.8 = 5.4
	 * - Combined: 2.08 * 5.4 = 11.2 (capped at 10.0)
	 */
	private double computeCost(double baseCostSeconds, List<HazardContribution> contributions) {
		if (contributions.isEmpty()) {
			return baseCostSeconds;
		}

		double multiplier = 1.0;

		for (HazardContribution contribution : contributions) {
			double hazardMultiplier = hazardCostConfig.computeMultiplier(
					contribution.hazardType(),
					contribution.severity()
			);
			multiplier *= hazardMultiplier;
		}

		// Cap total multiplier at 10x to prevent extreme values
		multiplier = Math.min(10.0, multiplier);

		return baseCostSeconds * multiplier;
	}

	/**
	 * Record to track a hazard's contribution to an edge.
	 *
	 * @param hazardId the hazard UUID
	 * @param hazardType the type of hazard (CRACKS, BLOCKED_SIDEWALK, etc.)
	 * @param severity the severity score (0-100) from Gemini analysis
	 */
	private record HazardContribution(UUID hazardId, HazardType hazardType, double severity) {}
}