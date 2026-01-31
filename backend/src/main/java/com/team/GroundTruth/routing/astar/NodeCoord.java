package com.team.GroundTruth.routing.astar;

/**
 * Minimal coordinate data for heuristic computation.
 *
 * @param lat latitude in decimal degrees
 * @param lon longitude in decimal degrees
 */
public record NodeCoord(double lat, double lon) {
}
