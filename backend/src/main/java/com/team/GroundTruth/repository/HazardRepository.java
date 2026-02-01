package com.team.GroundTruth.repository;

import com.team.GroundTruth.domain.entity.Hazard.Hazard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/**
 * Repository for querying hazard entities.
 */
public interface HazardRepository extends JpaRepository<Hazard, UUID> {

	/**
	 * Finds all hazards with labels matching the accessibility hazard types.
	 *
	 * @param labels the hazard labels to search for (e.g., "Cracks", "Blocked Sidewalk")
	 * @return hazards matching the labels
	 */
	@Query("""
			SELECT h FROM Hazard h
			JOIN FETCH h.report r
			WHERE LOWER(h.label) IN :labels
			AND r.latitude IS NOT NULL
			AND r.longitude IS NOT NULL
			""")
	List<Hazard> findByLabelsWithLocation(@Param("labels") List<String> labels);

	/**
	 * Finds all accessibility hazards (Cracks, Blocked Sidewalk) with location data.
	 *
	 * @return accessibility hazards with valid locations
	 */
	@Query("""
			SELECT h FROM Hazard h
			JOIN FETCH h.report r
			WHERE LOWER(h.label) IN ('cracks', 'blocked sidewalk')
			AND r.latitude IS NOT NULL
			AND r.longitude IS NOT NULL
			""")
	List<Hazard> findAccessibilityHazardsWithLocation();

	/**
	 * Finds accessibility hazards within a bounding box with severity above threshold.
	 *
	 * @param minLat minimum latitude
	 * @param maxLat maximum latitude
	 * @param minLon minimum longitude
	 * @param maxLon maximum longitude
	 * @param minSeverity minimum severity threshold (0-100)
	 * @return hazards within bounds above threshold
	 */
	@Query("""
			SELECT h FROM Hazard h
			JOIN FETCH h.report r
			WHERE LOWER(h.label) IN ('cracks', 'blocked sidewalk')
			AND r.latitude IS NOT NULL
			AND r.longitude IS NOT NULL
			AND r.latitude BETWEEN :minLat AND :maxLat
			AND r.longitude BETWEEN :minLon AND :maxLon
			AND (h.confidence IS NULL OR h.confidence >= :minSeverity)
			""")
	List<Hazard> findAccessibilityHazardsInBounds(
			@Param("minLat") float minLat,
			@Param("maxLat") float maxLat,
			@Param("minLon") float minLon,
			@Param("maxLon") float maxLon,
			@Param("minSeverity") double minSeverity
	);

	/**
	 * Finds all hazards within a bounding box with severity above threshold.
	 *
	 * @param minLat minimum latitude
	 * @param maxLat maximum latitude
	 * @param minLon minimum longitude
	 * @param maxLon maximum longitude
	 * @param minSeverity minimum severity threshold (0-100)
	 * @return all hazards within bounds above threshold
	 */
	@Query("""
			SELECT h FROM Hazard h
			JOIN FETCH h.report r
			WHERE r.latitude IS NOT NULL
			AND r.longitude IS NOT NULL
			AND r.latitude BETWEEN :minLat AND :maxLat
			AND r.longitude BETWEEN :minLon AND :maxLon
			AND (h.confidence IS NULL OR h.confidence >= :minSeverity)
			""")
	List<Hazard> findHazardsInBounds(
			@Param("minLat") float minLat,
			@Param("maxLat") float maxLat,
			@Param("minLon") float minLon,
			@Param("maxLon") float maxLon,
			@Param("minSeverity") double minSeverity
	);
}