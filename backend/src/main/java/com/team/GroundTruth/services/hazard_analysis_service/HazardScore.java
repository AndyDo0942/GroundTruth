package com.team.GroundTruth.services.hazard_analysis_service;

/**
 * Hazard classification result for a single hazard.
 *
 * @param hazardType hazard label
 * @param severityScore severity score 0-100
 */
public record HazardScore(String hazardType, Double severityScore) {}
