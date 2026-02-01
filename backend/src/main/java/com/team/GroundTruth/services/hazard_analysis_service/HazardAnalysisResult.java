package com.team.GroundTruth.services.hazard_analysis_service;

import java.util.List;

/**
 * Parsed result from AI hazard analysis.
 *
 * @param roadType identified environment (road or sidewalk)
 * @param hazards list of hazard scores
 */
public record HazardAnalysisResult(String roadType, List<HazardScore> hazards) {}
