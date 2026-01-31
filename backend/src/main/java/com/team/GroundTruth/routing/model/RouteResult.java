package com.team.GroundTruth.routing.model;

import java.util.List;

/**
 * Result of a routing computation.
 *
 * @param pathNodeIds ordered node ids from start to goal (inclusive)
 * @param pathEdgeIds ordered edge ids aligned to node transitions
 * @param distanceMeters total path length in meters
 * @param durationSeconds total travel time in seconds
 */
public record RouteResult(
		List<Long> pathNodeIds,
		List<Long> pathEdgeIds,
		double distanceMeters,
		double durationSeconds
) {
}
