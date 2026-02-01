package com.team.GroundTruth.controller;

import com.team.GroundTruth.domain.dto.walksafe.InitializeResponseDto;
import com.team.GroundTruth.routing.service.WalkAccessibilityService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

/**
 * REST controller for managing walk accessibility edge costs based on hazards
 * (Cracks, Blocked Sidewalk).
 */
@RestController
@RequestMapping(path = "/walk-accessibility", produces = MediaType.APPLICATION_JSON_VALUE)
public class WalkAccessibilityController {

	private final WalkAccessibilityService walkAccessibilityService;

	public WalkAccessibilityController(WalkAccessibilityService walkAccessibilityService) {
		this.walkAccessibilityService = Objects.requireNonNull(walkAccessibilityService, "walkAccessibilityService");
	}

	/**
	 * Initializes walk accessibility edge costs by processing all accessibility hazards
	 * (Cracks, Blocked Sidewalk) and computing costs for nearby walk edges.
	 *
	 * @return number of edges updated
	 */
	@PostMapping(path = "/initialize")
	public InitializeResponseDto initialize() {
		int count = walkAccessibilityService.initializeFromHazards();
		return new InitializeResponseDto(count);
	}
}