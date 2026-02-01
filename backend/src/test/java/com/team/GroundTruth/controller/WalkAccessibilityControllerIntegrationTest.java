package com.team.GroundTruth.controller;

import com.team.GroundTruth.entity.maps.WalkAccessibilityEdgeCostEntity;
import com.team.GroundTruth.routing.repo.WalkAccessibilityEdgeCostRepository;
import com.team.GroundTruth.routing.service.WalkAccessibilityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for WalkAccessibility controller using Testcontainers.
 * Tests the full flow from controller through service to database.
 */
@SpringBootTest
@Testcontainers
class WalkAccessibilityControllerIntegrationTest {

	@Container
	static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
			DockerImageName.parse("postgis/postgis:15-3.4")
					.asCompatibleSubstituteFor("postgres")
	);

	@DynamicPropertySource
	static void configureProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);
		registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
		registry.add("spring.docker.compose.enabled", () -> "false");
		registry.add("hazard.cost.effect-radius-meters", () -> "100.0");
	}

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private WalkAccessibilityService walkAccessibilityService;

	@Autowired
	private WalkAccessibilityEdgeCostRepository costRepository;

	@Autowired
	private WalkAccessibilityController walkAccessibilityController;

	private static final UUID HAZARD_1_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
	private static final UUID HAZARD_2_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
	private static final UUID USER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
	private static final UUID REPORT_1_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
	private static final UUID REPORT_2_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

	@BeforeEach
	void setUp() {
		jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS postgis");

		jdbcTemplate.execute("""
			CREATE TABLE IF NOT EXISTS nodes (
				id BIGSERIAL PRIMARY KEY,
				geom GEOMETRY(Point, 4326) NOT NULL
			)
		""");

		jdbcTemplate.execute("""
			CREATE TABLE IF NOT EXISTS edges (
				id BIGSERIAL PRIMARY KEY,
				source BIGINT NOT NULL REFERENCES nodes(id),
				target BIGINT NOT NULL REFERENCES nodes(id),
				geom GEOMETRY(LineString, 4326) NOT NULL,
				length_m DOUBLE PRECISION NOT NULL,
				cost_s DOUBLE PRECISION NOT NULL,
				attrs JSONB NOT NULL DEFAULT '{}'::jsonb,
				mode TEXT NOT NULL DEFAULT 'walk'
			)
		""");

		jdbcTemplate.execute("""
			CREATE TABLE IF NOT EXISTS users (
				id UUID PRIMARY KEY,
				username VARCHAR(50) NOT NULL UNIQUE
			)
		""");

		jdbcTemplate.execute("""
			CREATE TABLE IF NOT EXISTS hazard_reports (
				id UUID PRIMARY KEY,
				user_id UUID NOT NULL REFERENCES users(id),
				image_url TEXT,
				latitude REAL,
				longitude REAL,
				created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
			)
		""");

		jdbcTemplate.execute("""
			CREATE TABLE IF NOT EXISTS hazards (
				id UUID PRIMARY KEY,
				report_id UUID NOT NULL REFERENCES hazard_reports(id),
				label VARCHAR(50) NOT NULL,
				confidence DOUBLE PRECISION,
				created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
			)
		""");

		jdbcTemplate.execute("""
			CREATE TABLE IF NOT EXISTS walk_safe_modifiers (
				edge_id BIGINT PRIMARY KEY REFERENCES edges(id) ON DELETE CASCADE,
				pop_density DOUBLE PRECISION,
				streetlight DOUBLE PRECISION,
				crime_in_area DOUBLE PRECISION,
				updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
			)
		""");

		jdbcTemplate.execute("""
			CREATE TABLE IF NOT EXISTS walk_accessibility_edge_costs (
				edge_id BIGINT PRIMARY KEY REFERENCES edges(id) ON DELETE CASCADE,
				cost_seconds DOUBLE PRECISION NOT NULL,
				contributing_hazard_ids UUID[] NOT NULL DEFAULT '{}',
				updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
			)
		""");

		jdbcTemplate.execute("TRUNCATE hazards, hazard_reports, users, walk_accessibility_edge_costs, walk_safe_modifiers, edges, nodes CASCADE");

		jdbcTemplate.update("INSERT INTO users (id, username) VALUES (?, 'testuser')", USER_ID);

		jdbcTemplate.execute("""
			INSERT INTO nodes (id, geom) VALUES
			(1, ST_SetSRID(ST_MakePoint(-73.9857, 40.7580), 4326)),
			(2, ST_SetSRID(ST_MakePoint(-73.9850, 40.7585), 4326)),
			(3, ST_SetSRID(ST_MakePoint(-73.9845, 40.7590), 4326))
		""");

		jdbcTemplate.execute("""
			INSERT INTO edges (id, source, target, geom, length_m, cost_s, mode) VALUES
			(1, 1, 2, ST_SetSRID(ST_MakeLine(ST_MakePoint(-73.9857, 40.7580), ST_MakePoint(-73.9850, 40.7585)), 4326), 100.0, 60.0, 'walk'),
			(2, 2, 3, ST_SetSRID(ST_MakeLine(ST_MakePoint(-73.9850, 40.7585), ST_MakePoint(-73.9845, 40.7590)), 4326), 80.0, 48.0, 'walk'),
			(3, 1, 3, ST_SetSRID(ST_MakeLine(ST_MakePoint(-73.9857, 40.7580), ST_MakePoint(-73.9845, 40.7590)), 4326), 150.0, 30.0, 'drive')
		""");
	}

	private void insertHazardReport(UUID reportId, float lat, float lon) {
		jdbcTemplate.update(
				"INSERT INTO hazard_reports (id, user_id, latitude, longitude, created_at) VALUES (?, ?, ?, ?, NOW())",
				reportId, USER_ID, lat, lon
		);
	}

	private void insertHazard(UUID hazardId, UUID reportId, String label, Double confidence) {
		jdbcTemplate.update(
				"INSERT INTO hazards (id, report_id, label, confidence, created_at) VALUES (?, ?, ?, ?, NOW())",
				hazardId, reportId, label, confidence
		);
	}

	@Test
	void testControllerInitialize_createsEdgeCosts() {
		// Create hazard near edge 1
		insertHazardReport(REPORT_1_ID, 40.7582f, -73.9854f);
		insertHazard(HAZARD_1_ID, REPORT_1_ID, "Cracks", 50.0);

		var response = walkAccessibilityController.initialize();

		assertTrue(response.edgesInitialized() > 0, "Should initialize edges");

		List<WalkAccessibilityEdgeCostEntity> costs = costRepository.findByEdgeIds(new long[]{1L, 2L});
		assertFalse(costs.isEmpty(), "Should have cost entries");
	}

	@Test
	void testControllerInitialize_noHazards_returnsZero() {
		// No hazards created

		var response = walkAccessibilityController.initialize();

		assertEquals(0, response.edgesInitialized(), "Should return 0 when no hazards");

		List<WalkAccessibilityEdgeCostEntity> costs = costRepository.findByEdgeIds(new long[]{1L, 2L, 3L});
		assertTrue(costs.isEmpty(), "Should have no cost entries");
	}

	@Test
	void testControllerInitialize_onlyAccessibilityHazards() {
		// Create pothole (should be ignored) and cracks (should be processed)
		insertHazardReport(REPORT_1_ID, 40.7582f, -73.9854f);
		insertHazard(HAZARD_1_ID, REPORT_1_ID, "pothole", 80.0);

		insertHazardReport(REPORT_2_ID, 40.7583f, -73.9855f);
		insertHazard(HAZARD_2_ID, REPORT_2_ID, "Cracks", 50.0);

		var response = walkAccessibilityController.initialize();

		assertTrue(response.edgesInitialized() > 0, "Should process Cracks hazard");

		// Check that only Cracks hazard is tracked
		List<WalkAccessibilityEdgeCostEntity> costs = costRepository.findByEdgeIds(new long[]{1L});
		WalkAccessibilityEdgeCostEntity cost1 = costs.stream()
				.filter(c -> c.getEdgeId() == 1L)
				.findFirst()
				.orElse(null);

		if (cost1 != null) {
			// Should only have the Cracks hazard, not pothole
			assertEquals(1, cost1.getContributingHazardIds().length, "Should only track Cracks");
			assertEquals(HAZARD_2_ID, cost1.getContributingHazardIds()[0], "Should be Cracks hazard");
		}
	}

	@Test
	void testControllerInitialize_calculatesCorrectCost() {
		// Create Cracks hazard with severity 60
		insertHazardReport(REPORT_1_ID, 40.7582f, -73.9854f);
		insertHazard(HAZARD_1_ID, REPORT_1_ID, "Cracks", 60.0);

		walkAccessibilityController.initialize();

		List<WalkAccessibilityEdgeCostEntity> costs = costRepository.findByEdgeIds(new long[]{1L});
		WalkAccessibilityEdgeCostEntity cost1 = costs.stream()
				.filter(c -> c.getEdgeId() == 1L)
				.findFirst()
				.orElse(null);

		if (cost1 != null) {
			// CRACKS base=1.3, severity=60 -> 1.3 * (1 + 0.6) = 1.3 * 1.6 = 2.08
			double expectedCost = 60.0 * 2.08;
			assertEquals(expectedCost, cost1.getCostSeconds(), 0.1, "Cost should reflect CRACKS hazard");
		}
	}

	@Test
	void testControllerInitialize_blockedSidewalkHigherCost() {
		// Create Blocked Sidewalk hazard (higher base multiplier than Cracks)
		insertHazardReport(REPORT_1_ID, 40.7582f, -73.9854f);
		insertHazard(HAZARD_1_ID, REPORT_1_ID, "Blocked Sidewalk", 50.0);

		walkAccessibilityController.initialize();

		List<WalkAccessibilityEdgeCostEntity> costs = costRepository.findByEdgeIds(new long[]{1L});
		WalkAccessibilityEdgeCostEntity cost1 = costs.stream()
				.filter(c -> c.getEdgeId() == 1L)
				.findFirst()
				.orElse(null);

		if (cost1 != null) {
			// BLOCKED_SIDEWALK base=3.0, severity=50 -> 3.0 * (1 + 0.5) = 3.0 * 1.5 = 4.5
			double expectedCost = 60.0 * 4.5;
			assertEquals(expectedCost, cost1.getCostSeconds(), 0.1, "Blocked Sidewalk should have higher cost");
		}
	}

	@Test
	void testControllerInitialize_multipleHazards() {
		// Create two hazards affecting the same edge
		insertHazardReport(REPORT_1_ID, 40.7582f, -73.9854f);
		insertHazard(HAZARD_1_ID, REPORT_1_ID, "Cracks", 60.0);

		insertHazardReport(REPORT_2_ID, 40.7583f, -73.9853f);
		insertHazard(HAZARD_2_ID, REPORT_2_ID, "Blocked Sidewalk", 80.0);

		walkAccessibilityController.initialize();

		List<WalkAccessibilityEdgeCostEntity> costs = costRepository.findByEdgeIds(new long[]{1L});
		WalkAccessibilityEdgeCostEntity cost1 = costs.stream()
				.filter(c -> c.getEdgeId() == 1L)
				.findFirst()
				.orElse(null);

		if (cost1 != null) {
			assertEquals(2, cost1.getContributingHazardIds().length, "Should track both hazards");

			// CRACKS: 1.3 * 1.6 = 2.08
			// BLOCKED_SIDEWALK: 3.0 * 1.8 = 5.4
			// Combined: 2.08 * 5.4 = 11.232 (capped at 10.0)
			double expectedMultiplier = Math.min(10.0, 2.08 * 5.4);
			double expectedCost = 60.0 * expectedMultiplier;
			assertEquals(expectedCost, cost1.getCostSeconds(), 0.1, "Cost should combine multiplicatively (capped)");
		}
	}

	@Test
	void testControllerInitialize_driveEdgesNotAffected() {
		// Create hazard that would be near edge 3 (drive mode)
		insertHazardReport(REPORT_1_ID, 40.7582f, -73.9854f);
		insertHazard(HAZARD_1_ID, REPORT_1_ID, "Cracks", 50.0);

		walkAccessibilityController.initialize();

		// Edge 3 is drive mode, should not have accessibility cost
		List<WalkAccessibilityEdgeCostEntity> costs = costRepository.findByEdgeIds(new long[]{3L});
		assertTrue(costs.isEmpty(), "Drive edges should not have accessibility costs");
	}

	@Test
	void testServiceUpdateForHazard_addsHazardToEdges() {
		// Initialize with one hazard
		insertHazardReport(REPORT_1_ID, 40.7582f, -73.9854f);
		insertHazard(HAZARD_1_ID, REPORT_1_ID, "Cracks", 50.0);
		walkAccessibilityService.initializeFromHazards();

		// Add new hazard via service
		insertHazardReport(REPORT_2_ID, 40.7583f, -73.9855f);
		insertHazard(HAZARD_2_ID, REPORT_2_ID, "Blocked Sidewalk", 70.0);

		int updated = walkAccessibilityService.updateForHazard(
				HAZARD_2_ID, "Blocked Sidewalk", 40.7583, -73.9855, 70.0);

		assertTrue(updated > 0, "Should update edges");

		List<WalkAccessibilityEdgeCostEntity> costs = costRepository.findByEdgeIds(new long[]{1L});
		WalkAccessibilityEdgeCostEntity cost1 = costs.stream()
				.filter(c -> c.getEdgeId() == 1L)
				.findFirst()
				.orElse(null);

		if (cost1 != null) {
			assertEquals(2, cost1.getContributingHazardIds().length, "Should have 2 hazards");
		}
	}

	@Test
	void testServiceRemoveHazard_removesAndRecalculates() {
		// Create two hazards
		insertHazardReport(REPORT_1_ID, 40.7582f, -73.9854f);
		insertHazard(HAZARD_1_ID, REPORT_1_ID, "Cracks", 60.0);

		insertHazardReport(REPORT_2_ID, 40.7583f, -73.9853f);
		insertHazard(HAZARD_2_ID, REPORT_2_ID, "Blocked Sidewalk", 80.0);

		walkAccessibilityService.initializeFromHazards();

		// Remove CRACKS hazard
		int removed = walkAccessibilityService.removeHazard(HAZARD_1_ID);
		assertTrue(removed > 0, "Should remove hazard");

		List<WalkAccessibilityEdgeCostEntity> costs = costRepository.findByEdgeIds(new long[]{1L});
		WalkAccessibilityEdgeCostEntity cost1 = costs.stream()
				.filter(c -> c.getEdgeId() == 1L)
				.findFirst()
				.orElse(null);

		if (cost1 != null) {
			assertEquals(1, cost1.getContributingHazardIds().length, "Should have 1 hazard remaining");
			assertEquals(HAZARD_2_ID, cost1.getContributingHazardIds()[0], "Should be Blocked Sidewalk");

			// Cost should now only reflect Blocked Sidewalk: 3.0 * 1.8 = 5.4
			double expectedCost = 60.0 * 5.4;
			assertEquals(expectedCost, cost1.getCostSeconds(), 0.1);
		}
	}
}
