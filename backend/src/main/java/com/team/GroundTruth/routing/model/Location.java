package com.team.GroundTruth.routing.model;

/**
 * Geographic location expressed in latitude and longitude.
 *
 * @param lat latitude in decimal degrees
 * @param lon longitude in decimal degrees
 */
public record Location(double lat, double lon) {
}
