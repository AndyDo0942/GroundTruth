package com.team.GroundTruth.routing.repo;

import com.team.GroundTruth.entity.maps.EdgeCostOverlayEntity;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repository for temporary edge cost overlays.
 */
public interface EdgeCostOverlayRepository extends JpaRepository<EdgeCostOverlayEntity, Long> {

	/**
	 * Loads active overlays for the supplied edges and travel mode.
	 *
	 * @param edgeIds edge ids to filter
	 * @param mode travel mode identifier
	 * @param asOf timestamp used to determine validity
	 * @return active overlays
	 */
	@Query(value = """
			SELECT *
			FROM edge_cost_overlays
			WHERE edge_id IN (:edgeIds)
			  AND mode = :mode
			  AND (valid_from IS NULL OR valid_from <= :asOf)
			  AND (valid_to IS NULL OR valid_to >= :asOf)
			""", nativeQuery = true)
	List<EdgeCostOverlayEntity> findActiveOverlays(
			@Param("edgeIds") Collection<Long> edgeIds,
			@Param("mode") String mode,
			@Param("asOf") OffsetDateTime asOf
	);
}
