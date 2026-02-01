package com.team.GroundTruth.routing.repo;

import com.team.GroundTruth.entity.maps.WalkAccessibilityEdgeCostEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/**
 * Repository for walk accessibility edge costs (Cracks, Blocked Sidewalk hazards).
 */
public interface WalkAccessibilityEdgeCostRepository extends JpaRepository<WalkAccessibilityEdgeCostEntity, Long> {

	/**
	 * Finds walk accessibility costs for the given edge ids.
	 *
	 * @param edgeIds edge ids to look up
	 * @return matching walk accessibility costs
	 */
	@Query(value = """
			SELECT *
			FROM walk_accessibility_edge_costs
			WHERE edge_id = ANY(:edgeIds)
			""", nativeQuery = true)
	List<WalkAccessibilityEdgeCostEntity> findByEdgeIds(@Param("edgeIds") long[] edgeIds);

	/**
	 * Finds all edges affected by a specific hazard id.
	 *
	 * @param hazardId the hazard UUID to search for
	 * @return edges that have this hazard in their contributing_hazard_ids
	 */
	@Query(value = """
			SELECT *
			FROM walk_accessibility_edge_costs
			WHERE :hazardId = ANY(contributing_hazard_ids)
			""", nativeQuery = true)
	List<WalkAccessibilityEdgeCostEntity> findByContributingHazardId(@Param("hazardId") UUID hazardId);
}