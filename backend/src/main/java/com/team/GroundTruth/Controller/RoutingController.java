package com.team.GroundTruth.controller;

import com.team.GroundTruth.domain.dto.routing.RouteTimeRequestDto;
import com.team.GroundTruth.domain.dto.routing.RouteTimeResponseDto;
import com.team.GroundTruth.domain.dto.routing.RouteResponseDto;
import com.team.GroundTruth.routing.model.RouteResult;
import com.team.GroundTruth.routing.model.RouteType;
import com.team.GroundTruth.routing.model.TravelMode;
import com.team.GroundTruth.routing.repo.EdgeRepository;
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

	/**
	 * Creates a routing controller.
	 *
	 * @param routingService routing service
	 */
	/**
	 * Creates a routing controller.
	 *
	 * @param routingService routing service
	 * @param edgeRepository edge repository for geometry lookups
	 */
	public RoutingController(RoutingService routingService, EdgeRepository edgeRepository) {
		this.routingService = Objects.requireNonNull(routingService, "routingService");
		this.edgeRepository = Objects.requireNonNull(edgeRepository, "edgeRepository");
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
