package com.team.GroundTruth.controller;

import com.team.GroundTruth.domain.dto.routing.HazardMarkerDto;
import com.team.GroundTruth.domain.dto.routing.RiskMarkerDto;
import com.team.GroundTruth.domain.dto.routing.RouteMarkersDto;
import com.team.GroundTruth.domain.dto.routing.RouteTimeRequestDto;
import com.team.GroundTruth.domain.dto.routing.RouteTimeResponseDto;
import com.team.GroundTruth.domain.dto.routing.RouteResponseDto;
import com.team.GroundTruth.domain.entity.Hazard.Hazard;
import com.team.GroundTruth.entity.maps.WalkSafeModifierEntity;
import com.team.GroundTruth.repository.HazardRepository;
import com.team.GroundTruth.routing.model.RouteResult;
import com.team.GroundTruth.routing.model.RouteType;
import com.team.GroundTruth.routing.model.TravelMode;
import com.team.GroundTruth.routing.repo.EdgeRepository;
import com.team.GroundTruth.routing.repo.WalkSafeModifierRepository;
import com.team.GroundTruth.routing.service.RoutingService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller that exposes routing travel-time estimates.
 */
@RestController
@RequestMapping(path = "/routing", produces = MediaType.APPLICATION_JSON_VALUE)
public class RoutingController {

	private final RoutingService routingService;
	private final EdgeRepository edgeRepository;
	private final HazardRepository hazardRepository;
	private final WalkSafeModifierRepository walkSafeModifierRepository;

	/**
	 * Creates a routing controller.
	 *
	 * @param routingService routing service
	 * @param edgeRepository edge repository for geometry lookups
	 * @param hazardRepository hazard repository for marker lookups
	 * @param walkSafeModifierRepository modifier repository for risk markers
	 */
	public RoutingController(
			RoutingService routingService,
			EdgeRepository edgeRepository,
			HazardRepository hazardRepository,
			WalkSafeModifierRepository walkSafeModifierRepository
	) {
		this.routingService = Objects.requireNonNull(routingService, "routingService");
		this.edgeRepository = Objects.requireNonNull(edgeRepository, "edgeRepository");
		this.hazardRepository = Objects.requireNonNull(hazardRepository, "hazardRepository");
		this.walkSafeModifierRepository = Objects.requireNonNull(walkSafeModifierRepository, "walkSafeModifierRepository");
	}

	/**
	 * Estimates travel time between two coordinates.
	 *
	 * @param request routing request
	 * @return travel time response
	 */
	@PostMapping(path = "/time", consumes = MediaType.APPLICATION_JSON_VALUE)
	public RouteTimeResponseDto estimateTravelTime(@RequestBody RouteTimeRequestDto request) {
		Objects.requireNonNull(request, "request");
		Objects.requireNonNull(request.start(), "start");
		Objects.requireNonNull(request.end(), "end");

		double radiusMeters = request.radiusMeters() == null ? 0.0 : request.radiusMeters();
		TravelMode mode = request.mode() == null ? TravelMode.WALK : request.mode();

		RouteResult result = routingService.route(
				request.start().toLocation(),
				request.end().toLocation(),
				radiusMeters,
				mode
		);

		return new RouteTimeResponseDto(result.durationSeconds());
	}

	/**
	 * Returns the full route details including GeoJSON geometry.
	 *
	 * @param startLat start latitude
	 * @param startLon start longitude
	 * @param endLat end latitude
	 * @param endLon end longitude
	 * @param radiusMeters search radius in meters
	 * @return route response
	 */
	/**
	 * Returns a basic walking route (fastest, base costs only).
	 */
	@GetMapping(path = "/route")
	public RouteResponseDto route(
			@RequestParam double startLat,
			@RequestParam double startLon,
			@RequestParam double endLat,
			@RequestParam double endLon,
			@RequestParam(required = false) TravelMode travelMode,
			@RequestParam(required = false) Double radiusMeters
	) {
		double effectiveRadius = radiusMeters == null ? 0.0 : radiusMeters;
		if (travelMode == null) {
			travelMode = TravelMode.WALK;
		}

		RouteResult result = routingService.route(
				new com.team.GroundTruth.routing.model.Location(startLat, startLon),
				new com.team.GroundTruth.routing.model.Location(endLat, endLon),
				effectiveRadius,
				travelMode
		);
		return buildRouteResponse(result);
	}

	/**
	 * Returns a route using a specific route type for cost calculation.
	 * Route types: fastest, walk_safe, walk_accessible, walk_safe_accessible, drive_fastest, drive_safe
	 *
	 * @param startLat start latitude
	 * @param startLon start longitude
	 * @param endLat end latitude
	 * @param endLon end longitude
	 * @param routeType route type for cost strategy
	 * @param radiusMeters search radius in meters
	 * @return route response
	 */
	@GetMapping(path = "/route/type")
	public RouteResponseDto routeByType(
			@RequestParam double startLat,
			@RequestParam double startLon,
			@RequestParam double endLat,
			@RequestParam double endLon,
			@RequestParam String routeType,
			@RequestParam(required = false) Double radiusMeters
	) {
		RouteType type = RouteType.fromValue(routeType);
		if (type == null) {
			throw new IllegalArgumentException("Unknown route type: " + routeType +
					". Valid types: fastest, walk_safe, walk_accessible, walk_safe_accessible, drive_fastest, drive_safe");
		}

		double effectiveRadius = radiusMeters == null ? 0.0 : radiusMeters;
		RouteResult result = routingService.route(
				new com.team.GroundTruth.routing.model.Location(startLat, startLon),
				new com.team.GroundTruth.routing.model.Location(endLat, endLon),
				effectiveRadius,
				type
		);

		return buildRouteResponse(result);
	}

	/**
	 * Returns a safe walking route optimized for safety (avoids high-crime, poorly-lit areas).
	 */
	@GetMapping(path = "/route/walk/safe")
	public RouteResponseDto routeWalkSafe(
			@RequestParam double startLat,
			@RequestParam double startLon,
			@RequestParam double endLat,
			@RequestParam double endLon,
			@RequestParam(required = false) Double radiusMeters
	) {
		double effectiveRadius = radiusMeters == null ? 0.0 : radiusMeters;
		RouteResult result = routingService.routeWalkingSafe(
				new com.team.GroundTruth.routing.model.Location(startLat, startLon),
				new com.team.GroundTruth.routing.model.Location(endLat, endLon),
				effectiveRadius
		);
		return buildRouteResponse(result);
	}

	/**
	 * Returns an accessible walking route (avoids cracks, blocked sidewalks).
	 */
	@GetMapping(path = "/route/walk/accessible")
	public RouteResponseDto routeWalkAccessible(
			@RequestParam double startLat,
			@RequestParam double startLon,
			@RequestParam double endLat,
			@RequestParam double endLon,
			@RequestParam(required = false) Double radiusMeters
	) {
		double effectiveRadius = radiusMeters == null ? 0.0 : radiusMeters;
		RouteResult result = routingService.routeWalkingAccessible(
				new com.team.GroundTruth.routing.model.Location(startLat, startLon),
				new com.team.GroundTruth.routing.model.Location(endLat, endLon),
				effectiveRadius
		);
		return buildRouteResponse(result);
	}

	/**
	 * Returns a walking route optimized for both safety and accessibility.
	 */
	@GetMapping(path = "/route/walk/safe-accessible")
	public RouteResponseDto routeWalkSafeAccessible(
			@RequestParam double startLat,
			@RequestParam double startLon,
			@RequestParam double endLat,
			@RequestParam double endLon,
			@RequestParam(required = false) Double radiusMeters
	) {
		double effectiveRadius = radiusMeters == null ? 0.0 : radiusMeters;
		RouteResult result = routingService.routeWalkingSafeAccessible(
				new com.team.GroundTruth.routing.model.Location(startLat, startLon),
				new com.team.GroundTruth.routing.model.Location(endLat, endLon),
				effectiveRadius
		);
		return buildRouteResponse(result);
	}

	/**
	 * Returns a driving route (fastest, base costs only).
	 */
	@GetMapping(path = "/route/drive")
	public RouteResponseDto routeDrive(
			@RequestParam double startLat,
			@RequestParam double startLon,
			@RequestParam double endLat,
			@RequestParam double endLon,
			@RequestParam(required = false) Double radiusMeters
	) {
		double effectiveRadius = radiusMeters == null ? 0.0 : radiusMeters;
		RouteResult result = routingService.routeDriving(
				new com.team.GroundTruth.routing.model.Location(startLat, startLon),
				new com.team.GroundTruth.routing.model.Location(endLat, endLon),
				effectiveRadius
		);
		return buildRouteResponse(result);
	}

	/**
	 * Returns a safe driving route (avoids potholes, ice hazards).
	 */
	@GetMapping(path = "/route/drive/safe")
	public RouteResponseDto routeDriveSafe(
			@RequestParam double startLat,
			@RequestParam double startLon,
			@RequestParam double endLat,
			@RequestParam double endLon,
			@RequestParam(required = false) Double radiusMeters
	) {
		double effectiveRadius = radiusMeters == null ? 0.0 : radiusMeters;
		RouteResult result = routingService.routeDrivingSafe(
				new com.team.GroundTruth.routing.model.Location(startLat, startLon),
				new com.team.GroundTruth.routing.model.Location(endLat, endLon),
				effectiveRadius
		);
		return buildRouteResponse(result);
	}

	// ==================== MARKER ENDPOINTS ====================

	/**
	 * Returns hazard markers for walk accessible routes.
	 * Shows cracks, blocked sidewalks above the severity threshold.
	 *
	 * @param minLat minimum latitude of bounding box
	 * @param maxLat maximum latitude of bounding box
	 * @param minLon minimum longitude of bounding box
	 * @param maxLon maximum longitude of bounding box
	 * @param minSeverity minimum severity threshold (0-100), default 0
	 * @return hazard markers for map display
	 */
	@GetMapping(path = "/markers/hazards")
	public RouteMarkersDto getHazardMarkers(
			@RequestParam float minLat,
			@RequestParam float maxLat,
			@RequestParam float minLon,
			@RequestParam float maxLon,
			@RequestParam(required = false, defaultValue = "0") double minSeverity
	) {
		List<Hazard> hazards = hazardRepository.findAccessibilityHazardsInBounds(
				minLat, maxLat, minLon, maxLon, minSeverity
		);

		List<HazardMarkerDto> markers = hazards.stream()
				.filter(h -> h.getReport().getLatitude() != null && h.getReport().getLongitude() != null)
				.map(h -> new HazardMarkerDto(
						h.getId(),
						h.getReport().getLatitude().doubleValue(),
						h.getReport().getLongitude().doubleValue(),
						h.getLabel(),
						h.getConfidence() != null ? h.getConfidence() : 50.0,
						HazardMarkerDto.iconTypeFor(h.getLabel())
				))
				.toList();

		return RouteMarkersDto.hazardsOnly(markers);
	}

	/**
	 * Returns all hazard markers (including potholes, ice) for drive routes.
	 *
	 * @param minLat minimum latitude of bounding box
	 * @param maxLat maximum latitude of bounding box
	 * @param minLon minimum longitude of bounding box
	 * @param maxLon maximum longitude of bounding box
	 * @param minSeverity minimum severity threshold (0-100), default 0
	 * @return all hazard markers for map display
	 */
	@GetMapping(path = "/markers/hazards/all")
	public RouteMarkersDto getAllHazardMarkers(
			@RequestParam float minLat,
			@RequestParam float maxLat,
			@RequestParam float minLon,
			@RequestParam float maxLon,
			@RequestParam(required = false, defaultValue = "0") double minSeverity
	) {
		List<Hazard> hazards = hazardRepository.findHazardsInBounds(
				minLat, maxLat, minLon, maxLon, minSeverity
		);

		List<HazardMarkerDto> markers = hazards.stream()
				.filter(h -> h.getReport().getLatitude() != null && h.getReport().getLongitude() != null)
				.map(h -> new HazardMarkerDto(
						h.getId(),
						h.getReport().getLatitude().doubleValue(),
						h.getReport().getLongitude().doubleValue(),
						h.getLabel(),
						h.getConfidence() != null ? h.getConfidence() : 50.0,
						HazardMarkerDto.iconTypeFor(h.getLabel())
				))
				.toList();

		return RouteMarkersDto.hazardsOnly(markers);
	}

	/**
	 * Returns risk markers for walk safe routes.
	 * Shows areas with low lighting, high crime, or low population density.
	 *
	 * @param maxStreetlight max streetlight value (areas below are marked), default 0.4
	 * @param minCrime min crime value (areas above are marked), default 0.5
	 * @param maxDensity max density value (areas below are marked), default 0.3
	 * @return risk markers for map display
	 */
	@GetMapping(path = "/markers/risks")
	public RouteMarkersDto getRiskMarkers(
			@RequestParam(required = false, defaultValue = "0.4") double maxStreetlight,
			@RequestParam(required = false, defaultValue = "0.5") double minCrime,
			@RequestParam(required = false, defaultValue = "0.3") double maxDensity
	) {
		List<WalkSafeModifierEntity> riskyModifiers = walkSafeModifierRepository.findRiskyAreas(
				maxStreetlight, minCrime, maxDensity
		);

		if (riskyModifiers.isEmpty()) {
			return RouteMarkersDto.risksOnly(List.of());
		}

		// Get edge centroids for the risky edges
		long[] edgeIds = riskyModifiers.stream()
				.mapToLong(WalkSafeModifierEntity::getEdgeId)
				.toArray();
		Map<Long, double[]> centroidsByEdgeId = new HashMap<>();
		for (Object[] row : edgeRepository.findEdgeCentroidsByIds(edgeIds)) {
			Long edgeId = ((Number) row[0]).longValue();
			double lat = ((Number) row[1]).doubleValue();
			double lon = ((Number) row[2]).doubleValue();
			centroidsByEdgeId.put(edgeId, new double[]{lat, lon});
		}

		List<RiskMarkerDto> markers = new ArrayList<>();
		for (WalkSafeModifierEntity modifier : riskyModifiers) {
			double[] centroid = centroidsByEdgeId.get(modifier.getEdgeId());
			if (centroid == null) {
				continue;
			}

			// Add markers for each risk type that exceeds threshold
			if (modifier.getStreetlight() != null && modifier.getStreetlight() <= maxStreetlight) {
				markers.add(new RiskMarkerDto(
						centroid[0], centroid[1],
						"low_lighting",
						modifier.getStreetlight(),
						RiskMarkerDto.severityFor("low_lighting", modifier.getStreetlight()),
						RiskMarkerDto.iconTypeFor("low_lighting")
				));
			}
			if (modifier.getCrimeInArea() != null && modifier.getCrimeInArea() >= minCrime) {
				markers.add(new RiskMarkerDto(
						centroid[0], centroid[1],
						"high_crime",
						modifier.getCrimeInArea(),
						RiskMarkerDto.severityFor("high_crime", modifier.getCrimeInArea()),
						RiskMarkerDto.iconTypeFor("high_crime")
				));
			}
			if (modifier.getPopDensity() != null && modifier.getPopDensity() <= maxDensity) {
				markers.add(new RiskMarkerDto(
						centroid[0], centroid[1],
						"low_density",
						modifier.getPopDensity(),
						RiskMarkerDto.severityFor("low_density", modifier.getPopDensity()),
						RiskMarkerDto.iconTypeFor("low_density")
				));
			}
		}

		return RouteMarkersDto.risksOnly(markers);
	}

	/**
	 * Returns combined markers for walk safe + accessible routes.
	 */
	@GetMapping(path = "/markers/all")
	public RouteMarkersDto getAllMarkers(
			@RequestParam float minLat,
			@RequestParam float maxLat,
			@RequestParam float minLon,
			@RequestParam float maxLon,
			@RequestParam(required = false, defaultValue = "0") double minSeverity,
			@RequestParam(required = false, defaultValue = "0.4") double maxStreetlight,
			@RequestParam(required = false, defaultValue = "0.5") double minCrime,
			@RequestParam(required = false, defaultValue = "0.3") double maxDensity
	) {
		RouteMarkersDto hazards = getHazardMarkers(minLat, maxLat, minLon, maxLon, minSeverity);
		RouteMarkersDto risks = getRiskMarkers(maxStreetlight, minCrime, maxDensity);

		return new RouteMarkersDto(hazards.hazardMarkers(), risks.riskMarkers());
	}

	// ==================== HELPER METHODS ====================

	private RouteResponseDto buildRouteResponse(RouteResult result) {
		RouteResponseDto.GeoJsonFeature geoJson = buildGeoJson(result);
		return new RouteResponseDto(
				result.distanceMeters(),
				result.durationSeconds(),
				result.pathNodeIds(),
				result.pathEdgeIds(),
				geoJson
		);
	}

	private RouteResponseDto.GeoJsonFeature buildGeoJson(RouteResult result) {
		if (edgeRepository == null || result.pathEdgeIds().isEmpty()) {
			return new RouteResponseDto.GeoJsonFeature(
					"Feature",
					Map.of(),
					new RouteResponseDto.GeoJsonGeometry("LineString", List.of())
			);
		}

		Map<Long, org.locationtech.jts.geom.LineString> byId = new HashMap<>();
		edgeRepository.findAllById(result.pathEdgeIds()).forEach(edge -> {
			if (edge.getGeom() != null) {
				byId.put(edge.getId(), edge.getGeom());
			}
		});

		List<List<Double>> coordinates = new ArrayList<>();
		for (Long edgeId : result.pathEdgeIds()) {
			org.locationtech.jts.geom.LineString line = byId.get(edgeId);
			if (line == null) {
				continue;
			}
			org.locationtech.jts.geom.Coordinate[] coords = line.getCoordinates();
			for (int i = 0; i < coords.length; i++) {
				org.locationtech.jts.geom.Coordinate coord = coords[i];
				if (!coordinates.isEmpty()) {
					List<Double> last = coordinates.get(coordinates.size() - 1);
					if (Double.compare(last.get(0), coord.x) == 0 && Double.compare(last.get(1), coord.y) == 0) {
						continue;
					}
				}
				coordinates.add(List.of(coord.x, coord.y));
			}
		}

		return new RouteResponseDto.GeoJsonFeature(
				"Feature",
				Map.of(),
				new RouteResponseDto.GeoJsonGeometry("LineString", coordinates)
		);
	}
}
