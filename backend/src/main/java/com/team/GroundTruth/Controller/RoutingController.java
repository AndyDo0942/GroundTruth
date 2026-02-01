package com.team.GroundTruth.controller;

import com.team.GroundTruth.domain.dto.routing.RouteTimeRequestDto;
import com.team.GroundTruth.domain.dto.routing.RouteTimeResponseDto;
import com.team.GroundTruth.routing.model.RouteResult;
import com.team.GroundTruth.routing.model.TravelMode;
import com.team.GroundTruth.routing.service.RoutingService;
import java.util.Objects;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller that exposes routing travel-time estimates.
 */
@RestController
@RequestMapping(path = "/routing", produces = MediaType.APPLICATION_JSON_VALUE)
public class RoutingController {

	private static final double DEFAULT_RADIUS_METERS = 2_000.0;

	private final RoutingService routingService;

	/**
	 * Creates a routing controller.
	 *
	 * @param routingService routing service
	 */
	public RoutingController(RoutingService routingService) {
		this.routingService = Objects.requireNonNull(routingService, "routingService");
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

		double radiusMeters = request.radiusMeters() == null ? DEFAULT_RADIUS_METERS : request.radiusMeters();
		TravelMode mode = request.mode() == null ? TravelMode.WALK : request.mode();

		RouteResult result = routingService.route(
				request.start().toLocation(),
				request.end().toLocation(),
				radiusMeters,
				mode
		);

		return new RouteTimeResponseDto(result.durationSeconds());
	}
}
