package com.team.GroundTruth.routing.repo;

import com.team.GroundTruth.entity.maps.EdgeEntity;
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
			WHERE source = ANY(:nodeIds)
			  AND target = ANY(:nodeIds)
			""", nativeQuery = true)
	List<EdgeEntity> loadSubgraphEdges(@Param("nodeIds") long[] nodeIds);

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
			WHERE source = ANY(:nodeIds)
			  AND target = ANY(:nodeIds)
			  AND mode = :mode
			""", nativeQuery = true)
	List<EdgeEntity> loadSubgraphEdgesByMode(
			@Param("nodeIds") long[] nodeIds,
			@Param("mode") String mode
	);

	/**
	 * Finds all walk edges with their centroid coordinates.
	 *
	 * @return list of edge id, latitude, longitude arrays
	 */
	@Query(value = """
			SELECT e.id,
			       ST_Y(ST_Centroid(e.geom)) as lat,
			       ST_X(ST_Centroid(e.geom)) as lon
			FROM edges e
			WHERE e.mode = 'walk'
			""", nativeQuery = true)
	List<Object[]> findWalkEdgeCentroids();

	/**
	 * Finds centroid coordinates for specific edge IDs.
	 *
	 * @param edgeIds edge IDs to look up
	 * @return list of [edge_id, latitude, longitude] arrays
	 */
	@Query(value = """
			SELECT e.id,
			       ST_Y(ST_Centroid(e.geom)) as lat,
			       ST_X(ST_Centroid(e.geom)) as lon
			FROM edges e
			WHERE e.id = ANY(:edgeIds)
			""", nativeQuery = true)
	List<Object[]> findEdgeCentroidsByIds(@Param("edgeIds") long[] edgeIds);
}
