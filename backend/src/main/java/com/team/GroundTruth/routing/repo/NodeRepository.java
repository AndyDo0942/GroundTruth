package com.team.GroundTruth.routing.repo;

import com.team.GroundTruth.entity.maps.NodeEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repository for map nodes.
 */
public interface NodeRepository extends JpaRepository<NodeEntity, Long> {

	/**
	 * Finds the nearest node to the supplied coordinate using PostGIS KNN.
	 *
	 * @param lat latitude in decimal degrees
	 * @param lon longitude in decimal degrees
	 * @return nearest node, if any
	 */
	@Query(value = """
			SELECT *
			FROM nodes
			ORDER BY geom <-> ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)
			LIMIT 1
			""", nativeQuery = true)
	Optional<NodeEntity> snapNearestNode(@Param("lat") double lat, @Param("lon") double lon);

	/**
	 * Loads nodes within a radius of either endpoint using geography distance.
	 *
	 * @param startLat latitude of the start coordinate
	 * @param startLon longitude of the start coordinate
	 * @param endLat latitude of the end coordinate
	 * @param endLon longitude of the end coordinate
	 * @param radiusMeters search radius in meters
	 * @return nodes that are within the radius
	 */
	@Query(value = """
			SELECT *
			FROM nodes
			WHERE ST_DWithin(
				geom::geography,
				ST_SetSRID(ST_MakePoint(:startLon, :startLat), 4326)::geography,
				:radiusMeters
			)
			OR ST_DWithin(
				geom::geography,
				ST_SetSRID(ST_MakePoint(:endLon, :endLat), 4326)::geography,
				:radiusMeters
			)
			""", nativeQuery = true)
	List<NodeEntity> loadSubgraphNodes(
			@Param("startLat") double startLat,
			@Param("startLon") double startLon,
			@Param("endLat") double endLat,
			@Param("endLon") double endLon,
			@Param("radiusMeters") double radiusMeters
	);
}
