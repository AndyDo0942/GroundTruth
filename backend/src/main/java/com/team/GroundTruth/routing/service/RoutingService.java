package com.team.GroundTruth.routing.service;

import com.team.GroundTruth.routing.model.Location;
import com.team.GroundTruth.routing.model.RouteResult;
import com.team.GroundTruth.routing.model.RouteType;
import com.team.GroundTruth.routing.model.TravelMode;

/**
 * Service for walking-path routing over the map graph.
 */
public interface RoutingService {

	/**
	 * Computes a route between two coordinates using an in-memory A* search.
	 *
	 * @param start start coordinate
	 * @param end end coordinate
	 * @param radiusMeters search radius in meters used to extract a subgraph
	 * @return routing result with node and edge paths
	 */
	RouteResult route(Location start, Location end, double radiusMeters);

	/**
	 * Computes a route between two coordinates using the requested travel mode.
	 *
	 * @param start start coordinate
	 * @param end end coordinate
	 * @param radiusMeters search radius in meters used to extract a subgraph
	 * @param mode travel mode
	 * @return routing result with node and edge paths
	 */
	RouteResult route(Location start, Location end, double radiusMeters, TravelMode mode);

	/**
	 * Computes a walking route between two coordinates.
	 *
	 * @param start start coordinate
	 * @param end end coordinate
	 * @param radiusMeters search radius in meters used to extract a subgraph
	 * @return routing result with node and edge paths
	 */
	RouteResult routeWalking(Location start, Location end, double radiusMeters);

	/**
	 * Computes a driving route between two coordinates.
	 *
	 * @param start start coordinate
	 * @param end end coordinate
	 * @param radiusMeters search radius in meters used to extract a subgraph
	 * @return routing result with node and edge paths
	 */
	RouteResult routeDriving(Location start, Location end, double radiusMeters);

	/**
	 * Computes a route using a specific route type (cost strategy).
	 * Route types determine which pre-computed cost tables are used:
	 * - FASTEST: base edge costs only
	 * - WALK_SAFE: uses walk_safe_edge_costs (safety factors)
	 * - WALK_ACCESSIBLE: uses walk_accessibility_edge_costs (hazard avoidance)
	 * - WALK_SAFE_ACCESSIBLE: combines both safety and accessibility costs
	 * - DRIVE_FASTEST: base driving costs
	 * - DRIVE_SAFE: uses drive_edge_costs for hazard avoidance
	 *
	 * @param start start coordinate
	 * @param end end coordinate
	 * @param radiusMeters search radius in meters used to extract a subgraph
	 * @param routeType the route type determining cost strategy
	 * @return routing result with node and edge paths
	 */
	RouteResult route(Location start, Location end, double radiusMeters, RouteType routeType);

	/**
	 * Computes a safe walking route avoiding high-crime, poorly-lit areas.
	 *
	 * @param start start coordinate
	 * @param end end coordinate
	 * @param radiusMeters search radius in meters
	 * @return routing result optimized for safety
	 */
	RouteResult routeWalkingSafe(Location start, Location end, double radiusMeters);

	/**
	 * Computes an accessible walking route avoiding cracks and blocked sidewalks.
	 *
	 * @param start start coordinate
	 * @param end end coordinate
	 * @param radiusMeters search radius in meters
	 * @return routing result optimized for accessibility
	 */
	RouteResult routeWalkingAccessible(Location start, Location end, double radiusMeters);

	/**
	 * Computes a walking route optimized for both safety and accessibility.
	 *
	 * @param start start coordinate
	 * @param end end coordinate
	 * @param radiusMeters search radius in meters
	 * @return routing result optimized for safety and accessibility
	 */
	RouteResult routeWalkingSafeAccessible(Location start, Location end, double radiusMeters);

	/**
	 * Computes a driving route avoiding hazards like potholes and ice.
	 *
	 * @param start start coordinate
	 * @param end end coordinate
	 * @param radiusMeters search radius in meters
	 * @return routing result optimized for hazard avoidance
	 */
	RouteResult routeDrivingSafe(Location start, Location end, double radiusMeters);
}
