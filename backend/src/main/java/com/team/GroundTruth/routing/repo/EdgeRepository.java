package com.team.GroundTruth.routing.repo;

import com.team.GroundTruth.entity.maps.EdgeEntity;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repository for directed map edges.
 */
public interface EdgeRepository extends JpaRepository<EdgeEntity, Long> {

	/**
	 * Loads edges whose source and target are within the supplied node id set.
	 *
	 * @param nodeIds node ids defining the subgraph
	 * @return matching directed edges
	 */
	@Query(value = """
			SELECT *
			FROM edges
			WHERE source IN (:nodeIds)
			  AND target IN (:nodeIds)
			""", nativeQuery = true)
	List<EdgeEntity> loadSubgraphEdges(@Param("nodeIds") Collection<Long> nodeIds);

	/**
	 * Loads edges whose source and target are within the supplied node id set
	 * and match the requested travel mode.
	 *
	 * @param nodeIds node ids defining the subgraph
	 * @param mode travel mode identifier
	 * @return matching directed edges
	 */
	@Query(value = """
			SELECT *
			FROM edges
			WHERE source IN (:nodeIds)
			  AND target IN (:nodeIds)
			  AND mode = :mode
			""", nativeQuery = true)
	List<EdgeEntity> loadSubgraphEdgesByMode(
			@Param("nodeIds") Collection<Long> nodeIds,
			@Param("mode") String mode
	);
}
