package com.team.GroundTruth.routing.service;

import com.team.GroundTruth.routing.model.Location;
import com.team.GroundTruth.routing.model.RouteResult;
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
}
