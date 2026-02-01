package com.team.GroundTruth.domain.dto.routing;

import java.util.List;

/**
 * DTO containing map markers for route visualization.
 * Returns hazard and risk markers based on route type.
 *
 * @param hazardMarkers accessibility hazard markers (cracks, blocked sidewalks)
 * @param riskMarkers safety risk markers (low lighting, crime, isolated areas)
 */
public record RouteMarkersDto(
		List<HazardMarkerDto> hazardMarkers,
		List<RiskMarkerDto> riskMarkers
) {
	/**
	 * Creates a response with only hazard markers.
	 */
	public static RouteMarkersDto hazardsOnly(List<HazardMarkerDto> hazards) {
		return new RouteMarkersDto(hazards, List.of());
	}

	/**
	 * Creates a response with only risk markers.
	 */
	public static RouteMarkersDto risksOnly(List<RiskMarkerDto> risks) {
		return new RouteMarkersDto(List.of(), risks);
	}
}