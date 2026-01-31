package com.team.GroundTruth.routing.service;

import com.team.GroundTruth.routing.model.Location;
import com.team.GroundTruth.routing.model.RouteResult;

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
}
