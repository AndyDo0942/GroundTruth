package com.team.GroundTruth.routing.astar;

/**
 * Directed edge with the data needed for in-memory routing.
 *
 * @param edgeId edge identifier
 * @param targetId target node identifier
 * @param lengthMeters edge length in meters
 * @param costSeconds traversal cost in seconds
 */
public record DirectedEdge(long edgeId, long targetId, double lengthMeters, double costSeconds) {
}
