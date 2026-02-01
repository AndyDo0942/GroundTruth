package com.team.GroundTruth.routing.repo;

import com.team.GroundTruth.entity.maps.WalkSafeModifierEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repository for walk safety modifiers.
 */
public interface WalkSafeModifierRepository extends JpaRepository<WalkSafeModifierEntity, Long> {

	/**
	 * Finds edges that intersect with a point within a given radius.
	 *
	 * @param lon longitude of the point
	 * @param lat latitude of the point
	 * @param radiusMeters search radius in meters
	 * @return edge ids that intersect the search area
	 */
	@Query(value = """
			SELECT e.id
			FROM edges e
			WHERE e.mode = 'walk'
			  AND ST_DWithin(
			      e.geom::geography,
			      ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography,
			      :radiusMeters
			  )
			""", nativeQuery = true)
	List<Long> findWalkEdgeIdsNearPoint(
			@Param("lon") double lon,
			@Param("lat") double lat,
			@Param("radiusMeters") double radiusMeters
	);

	/**
	 * Finds modifiers for the given edge ids.
	 *
	 * @param edgeIds edge ids to look up
	 * @return matching modifiers
	 */
	@Query(value = """
			SELECT *
			FROM walk_safe_modifiers
			WHERE edge_id = ANY(:edgeIds)
			""", nativeQuery = true)
	List<WalkSafeModifierEntity> findByEdgeIds(@Param("edgeIds") long[] edgeIds);

	/**
	 * Finds modifiers with low streetlight values (risky for lighting).
	 *
	 * @param maxStreetlight maximum streetlight value (areas below this are risky)
	 * @return modifiers with low lighting
	 */
	@Query("""
			SELECT m FROM WalkSafeModifierEntity m
			WHERE m.streetlight IS NOT NULL
			AND m.streetlight <= :maxStreetlight
			""")
	List<WalkSafeModifierEntity> findLowLightingAreas(@Param("maxStreetlight") double maxStreetlight);

	/**
	 * Finds modifiers with high crime values.
	 *
	 * @param minCrime minimum crime value (areas above this are risky)
	 * @return modifiers with high crime
	 */
	@Query("""
			SELECT m FROM WalkSafeModifierEntity m
			WHERE m.crimeInArea IS NOT NULL
			AND m.crimeInArea >= :minCrime
			""")
	List<WalkSafeModifierEntity> findHighCrimeAreas(@Param("minCrime") double minCrime);

	/**
	 * Finds modifiers with low population density (isolated areas).
	 *
	 * @param maxDensity maximum density value (areas below this are risky)
	 * @return modifiers with low density
	 */
	@Query("""
			SELECT m FROM WalkSafeModifierEntity m
			WHERE m.popDensity IS NOT NULL
			AND m.popDensity <= :maxDensity
			""")
	List<WalkSafeModifierEntity> findLowDensityAreas(@Param("maxDensity") double maxDensity);

	/**
	 * Finds all modifiers that exceed any risk threshold.
	 *
	 * @param maxStreetlight max streetlight value (below = risky)
	 * @param minCrime min crime value (above = risky)
	 * @param maxDensity max density value (below = risky)
	 * @return modifiers that are risky by any measure
	 */
	@Query("""
			SELECT m FROM WalkSafeModifierEntity m
			WHERE (m.streetlight IS NOT NULL AND m.streetlight <= :maxStreetlight)
			   OR (m.crimeInArea IS NOT NULL AND m.crimeInArea >= :minCrime)
			   OR (m.popDensity IS NOT NULL AND m.popDensity <= :maxDensity)
			""")
	List<WalkSafeModifierEntity> findRiskyAreas(
			@Param("maxStreetlight") double maxStreetlight,
			@Param("minCrime") double minCrime,
			@Param("maxDensity") double maxDensity
	);
}