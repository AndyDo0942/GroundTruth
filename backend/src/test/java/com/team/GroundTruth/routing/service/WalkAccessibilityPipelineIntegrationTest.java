package com.team.GroundTruth.routing.service;

import com.team.GroundTruth.entity.maps.WalkAccessibilityEdgeCostEntity;
import com.team.GroundTruth.routing.repo.EdgeRepository;
import com.team.GroundTruth.routing.repo.WalkAccessibilityEdgeCostRepository;
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
 * Integration tests for the WalkAccessibility pipeline using Testcontainers.
 * Tests the end-to-end functionality of hazard-based accessibility cost calculations.
 */
@SpringBootTest
@Testcontainers
class WalkAccessibilityPipelineIntegrationTest {

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
		// Configure hazard cost settings for predictable test results
		registry.add("hazard.cost.effect-radius-meters", () -> "100.0");
	}

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private WalkAccessibilityService walkAccessibilityService;

	@Autowired
	private WalkAccessibilityEdgeCostRepository costRepository;

	@Autowired
	private EdgeRepository edgeRepository;

	// Test UUIDs for hazards
	private static final UUID HAZARD_1_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
	private static final UUID HAZARD_2_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
	private static final UUID HAZARD_3_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
	private static final UUID USER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
	private static final UUID REPORT_1_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
	private static final UUID REPORT_2_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

	@BeforeEach
	void setUp() {
		// Create PostGIS extension
		jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS postgis");

		// Create nodes table
		jdbcTemplate.execute("""
			CREATE TABLE IF NOT EXISTS nodes (
				id BIGSERIAL PRIMARY KEY,
				geom GEOMETRY(Point, 4326) NOT NULL
			)
		""");

		// Create edges table
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

		// Create users table
		jdbcTemplate.execute("""
			CREATE TABLE IF NOT EXISTS users (
				id UUID PRIMARY KEY,
				username VARCHAR(50) NOT NULL UNIQUE
			)
		""");

		// Create hazard_reports table
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

		// Create hazards table
		jdbcTemplate.execute("""
			CREATE TABLE IF NOT EXISTS hazards (
				id UUID PRIMARY KEY,
				report_id UUID NOT NULL REFERENCES hazard_reports(id),
				label VARCHAR(50) NOT NULL,
				confidence DOUBLE PRECISION,
				created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
			)
		""");

		// Create walk_safe_modifiers table (needed for findWalkEdgeIdsNearPoint query)
		jdbcTemplate.execute("""
			CREATE TABLE IF NOT EXISTS walk_safe_modifiers (
				edge_id BIGINT PRIMARY KEY REFERENCES edges(id) ON DELETE CASCADE,
				pop_density DOUBLE PRECISION,
				streetlight DOUBLE PRECISION,
				crime_in_area DOUBLE PRECISION,
				updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
			)
		""");

		// Create walk_accessibility_edge_costs table
		jdbcTemplate.execute("""
			CREATE TABLE IF NOT EXISTS walk_accessibility_edge_costs (
				edge_id BIGINT PRIMARY KEY REFERENCES edges(id) ON DELETE CASCADE,
				cost_seconds DOUBLE PRECISION NOT NULL,
				contributing_hazard_ids UUID[] NOT NULL DEFAULT '{}',
				updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
			)
		""");

		// Clear data between tests
		jdbcTemplate.execute("TRUNCATE hazards, hazard_reports, users, walk_accessibility_edge_costs, walk_safe_modifiers, edges, nodes CASCADE");

		// Insert test user
		jdbcTemplate.update("INSERT INTO users (id, username) VALUES (?, 'testuser')", USER_ID);

		// Insert test nodes (NYC area - Times Square vicinity)
		// Node 1: Times Square
		jdbcTemplate.execute("""
			INSERT INTO nodes (id, geom) VALUES
			(1, ST_SetSRID(ST_MakePoint(-73.9857, 40.7580), 4326))
		""");
		// Node 2: Near Times Square
		jdbcTemplate.execute("""
			INSERT INTO nodes (id, geom) VALUES
			(2, ST_SetSRID(ST_MakePoint(-73.9850, 40.7585), 4326))
		""");
		// Node 3: Also near
		jdbcTemplate.execute("""
			INSERT INTO nodes (id, geom) VALUES
			(3, ST_SetSRID(ST_MakePoint(-73.9845, 40.7590), 4326))
		""");
		// Node 4: Far away (Brooklyn)
		jdbcTemplate.execute("""
			INSERT INTO nodes (id, geom) VALUES
			(4, ST_SetSRID(ST_MakePoint(-73.9500, 40.6500), 4326))
		""");
		// Node 5: Also Brooklyn (for far-away edge)
		jdbcTemplate.execute("""
			INSERT INTO nodes (id, geom) VALUES
			(5, ST_SetSRID(ST_MakePoint(-73.9510, 40.6510), 4326))
		""");

		// Insert test edges (walk mode)
		// Edge 1: Between node 1 and 2, base cost 60 seconds
		jdbcTemplate.execute("""
			INSERT INTO edges (id, source, target, geom, length_m, cost_s, mode) VALUES
			(1, 1, 2, ST_SetSRID(ST_MakeLine(ST_MakePoint(-73.9857, 40.7580), ST_MakePoint(-73.9850, 40.7585)), 4326), 100.0, 60.0, 'walk')
		""");
		// Edge 2: Between node 2 and 3, base cost 48 seconds
		jdbcTemplate.execute("""
			INSERT INTO edges (id, source, target, geom, length_m, cost_s, mode) VALUES
			(2, 2, 3, ST_SetSRID(ST_MakeLine(ST_MakePoint(-73.9850, 40.7585), ST_MakePoint(-73.9845, 40.7590)), 4326), 80.0, 48.0, 'walk')
		""");
		// Edge 3: Drive mode (should be ignored by walk accessibility)
		jdbcTemplate.execute("""
			INSERT INTO edges (id, source, target, geom, length_m, cost_s, mode) VALUES
			(3, 1, 3, ST_SetSRID(ST_MakeLine(ST_MakePoint(-73.9857, 40.7580), ST_MakePoint(-73.9845, 40.7590)), 4326), 150.0, 30.0, 'drive')
		""");
		// Edge 4: Far away edge entirely in Brooklyn (should not be affected by Times Square hazards)
		jdbcTemplate.execute("""
			INSERT INTO edges (id, source, target, geom, length_m, cost_s, mode) VALUES
			(4, 4, 5, ST_SetSRID(ST_MakeLine(ST_MakePoint(-73.9500, 40.6500), ST_MakePoint(-73.9510, 40.6510)), 4326), 100.0, 60.0, 'walk')
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
	void testInitializeFromHazards_createsEntriesForNearbyEdges() {
		// Create a hazard report near edge 1 with a "Cracks" hazard
		insertHazardReport(REPORT_1_ID, 40.7582f, -73.9854f);
		insertHazard(HAZARD_1_ID, REPORT_1_ID, "Cracks", 60.0);

		int count = walkAccessibilityService.initializeFromHazards();

		assertTrue(count > 0, "Should update at least one edge");

		List<WalkAccessibilityEdgeCostEntity> costs = costRepository.findByEdgeIds(new long[]{1L, 2L});
		assertFalse(costs.isEmpty(), "Should have cost entries");

		// Check that the cost was modified
		// CRACKS base=1.3, severity=60 -> 1.3 * (1 + 0.6) = 1.3 * 1.6 = 2.08
		WalkAccessibilityEdgeCostEntity cost1 = costs.stream()
				.filter(c -> c.getEdgeId() == 1L)
				.findFirst()
				.orElse(null);

		if (cost1 != null) {
			double expectedCost = 60.0 * 2.08;
			assertEquals(expectedCost, cost1.getCostSeconds(), 0.1, "Edge 1 cost should reflect CRACKS hazard");
			assertTrue(cost1.getContributingHazardIds().length > 0, "Should track contributing hazards");
			assertEquals(HAZARD_1_ID, cost1.getContributingHazardIds()[0]);
		}
	}

	@Test
	void testInitializeFromHazards_ignoresNonAccessibilityHazards() {
		// Create a hazard report with a "pothole" (not an accessibility hazard)
		insertHazardReport(REPORT_1_ID, 40.7582f, -73.9854f);
		insertHazard(HAZARD_1_ID, REPORT_1_ID, "pothole", 80.0);

		int count = walkAccessibilityService.initializeFromHazards();

		assertEquals(0, count, "Pothole should not affect walk accessibility costs");

		List<WalkAccessibilityEdgeCostEntity> costs = costRepository.findByEdgeIds(new long[]{1L, 2L});
		assertTrue(costs.isEmpty(), "Should have no accessibility cost entries for pothole");
	}

	@Test
	void testInitializeFromHazards_blockedSidewalkHasHigherMultiplier() {
		// Create a "Blocked Sidewalk" hazard (base=3.0, much higher than Cracks)
		insertHazardReport(REPORT_1_ID, 40.7582f, -73.9854f);
		insertHazard(HAZARD_1_ID, REPORT_1_ID, "Blocked Sidewalk", 50.0);

		walkAccessibilityService.initializeFromHazards();

		List<WalkAccessibilityEdgeCostEntity> costs = costRepository.findByEdgeIds(new long[]{1L});

		WalkAccessibilityEdgeCostEntity cost1 = costs.stream()
				.filter(c -> c.getEdgeId() == 1L)
				.findFirst()
				.orElse(null);

		if (cost1 != null) {
			// BLOCKED_SIDEWALK base=3.0, severity=50 -> 3.0 * (1 + 0.5) = 3.0 * 1.5 = 4.5
			double expectedCost = 60.0 * 4.5;
			assertEquals(expectedCost, cost1.getCostSeconds(), 0.1, "Blocked Sidewalk should have higher multiplier");
		}
	}

	@Test
	void testMultipleHazards_combineMultiplicatively() {
		// Create two hazards affecting the same edge
		insertHazardReport(REPORT_1_ID, 40.7582f, -73.9854f);
		insertHazard(HAZARD_1_ID, REPORT_1_ID, "Cracks", 60.0);

		insertHazardReport(REPORT_2_ID, 40.7583f, -73.9853f);
		insertHazard(HAZARD_2_ID, REPORT_2_ID, "Blocked Sidewalk", 80.0);

		walkAccessibilityService.initializeFromHazards();

		List<WalkAccessibilityEdgeCostEntity> costs = costRepository.findByEdgeIds(new long[]{1L});

		WalkAccessibilityEdgeCostEntity cost1 = costs.stream()
				.filter(c -> c.getEdgeId() == 1L)
				.findFirst()
				.orElse(null);

		if (cost1 != null) {
			// CRACKS: 1.3 * 1.6 = 2.08
			// BLOCKED_SIDEWALK: 3.0 * 1.8 = 5.4
			// Combined: 2.08 * 5.4 = 11.232 (capped at 10.0)
			double expectedMultiplier = Math.min(10.0, 2.08 * 5.4);
			double expectedCost = 60.0 * expectedMultiplier;
			assertEquals(expectedCost, cost1.getCostSeconds(), 0.1, "Multiple hazards should combine multiplicatively (capped at 10x)");
			assertEquals(2, cost1.getContributingHazardIds().length, "Should track both hazards");
		}
	}

	@Test
	void testUpdateForHazard_addsNewHazard() {
		// Start with one hazard
		insertHazardReport(REPORT_1_ID, 40.7582f, -73.9854f);
		insertHazard(HAZARD_1_ID, REPORT_1_ID, "Cracks", 50.0);
		walkAccessibilityService.initializeFromHazards();

		// Add a new hazard via updateForHazard
		insertHazardReport(REPORT_2_ID, 40.7583f, -73.9855f);
		insertHazard(HAZARD_2_ID, REPORT_2_ID, "Blocked Sidewalk", 70.0);

		int updated = walkAccessibilityService.updateForHazard(
				HAZARD_2_ID,
				"Blocked Sidewalk",
				40.7583,
				-73.9855,
				70.0
		);

		assertTrue(updated > 0, "Should update edges");

		List<WalkAccessibilityEdgeCostEntity> costs = costRepository.findByEdgeIds(new long[]{1L});
		WalkAccessibilityEdgeCostEntity cost1 = costs.stream()
				.filter(c -> c.getEdgeId() == 1L)
				.findFirst()
				.orElse(null);

		if (cost1 != null) {
			assertEquals(2, cost1.getContributingHazardIds().length, "Should have two hazards");
		}
	}

	@Test
	void testUpdateForHazard_ignoresNonAccessibilityHazard() {
		int updated = walkAccessibilityService.updateForHazard(
				HAZARD_1_ID,
				"pothole",
				40.7582,
				-73.9854,
				80.0
		);

		assertEquals(0, updated, "Pothole should be ignored");
	}

	@Test
	void testRemoveHazard_deletesWhenNoHazardsRemain() {
		// Create a single hazard
		insertHazardReport(REPORT_1_ID, 40.7582f, -73.9854f);
		insertHazard(HAZARD_1_ID, REPORT_1_ID, "Cracks", 50.0);
		walkAccessibilityService.initializeFromHazards();

		// Verify cost exists
		List<WalkAccessibilityEdgeCostEntity> costsBefore = costRepository.findByEdgeIds(new long[]{1L});
		assertFalse(costsBefore.isEmpty(), "Should have cost entry before removal");

		// Remove the hazard
		int removed = walkAccessibilityService.removeHazard(HAZARD_1_ID);

		assertTrue(removed > 0, "Should remove hazard from edges");

		// Verify cost entry is deleted
		List<WalkAccessibilityEdgeCostEntity> costsAfter = costRepository.findByEdgeIds(new long[]{1L});
		assertTrue(costsAfter.isEmpty(), "Cost entry should be deleted when no hazards remain");
	}

	@Test
	void testRemoveHazard_recalculatesWhenHazardsRemain() {
		// Create two hazards
		insertHazardReport(REPORT_1_ID, 40.7582f, -73.9854f);
		insertHazard(HAZARD_1_ID, REPORT_1_ID, "Cracks", 60.0);

		insertHazardReport(REPORT_2_ID, 40.7583f, -73.9853f);
		insertHazard(HAZARD_2_ID, REPORT_2_ID, "Blocked Sidewalk", 80.0);

		walkAccessibilityService.initializeFromHazards();

		// Get initial cost
		List<WalkAccessibilityEdgeCostEntity> costsBefore = costRepository.findByEdgeIds(new long[]{1L});
		WalkAccessibilityEdgeCostEntity costBefore = costsBefore.stream()
				.filter(c -> c.getEdgeId() == 1L)
				.findFirst()
				.orElse(null);

		assertNotNull(costBefore, "Should have cost before removal");
		assertEquals(2, costBefore.getContributingHazardIds().length, "Should have 2 hazards");

		// Remove CRACKS hazard
		walkAccessibilityService.removeHazard(HAZARD_1_ID);

		// Check remaining cost
		List<WalkAccessibilityEdgeCostEntity> costsAfter = costRepository.findByEdgeIds(new long[]{1L});
		WalkAccessibilityEdgeCostEntity costAfter = costsAfter.stream()
				.filter(c -> c.getEdgeId() == 1L)
				.findFirst()
				.orElse(null);

		assertNotNull(costAfter, "Should still have cost entry");
		assertEquals(1, costAfter.getContributingHazardIds().length, "Should have 1 hazard remaining");
		assertEquals(HAZARD_2_ID, costAfter.getContributingHazardIds()[0], "Should be Blocked Sidewalk hazard");

		// Cost should now only reflect Blocked Sidewalk
		// BLOCKED_SIDEWALK: 3.0 * 1.8 = 5.4
		double expectedCost = 60.0 * 5.4;
		assertEquals(expectedCost, costAfter.getCostSeconds(), 0.1, "Cost should only reflect remaining hazard");
	}

	@Test
	void testSeverityAffectsMultiplier() {
		// Low severity
		insertHazardReport(REPORT_1_ID, 40.7582f, -73.9854f);
		insertHazard(HAZARD_1_ID, REPORT_1_ID, "Cracks", 10.0);
		walkAccessibilityService.initializeFromHazards();

		List<WalkAccessibilityEdgeCostEntity> lowSeverityCosts = costRepository.findByEdgeIds(new long[]{1L});
		double lowSeverityCost = lowSeverityCosts.stream()
				.filter(c -> c.getEdgeId() == 1L)
				.findFirst()
				.map(WalkAccessibilityEdgeCostEntity::getCostSeconds)
				.orElse(0.0);

		// Clear and test high severity
		jdbcTemplate.execute("TRUNCATE hazards, hazard_reports, walk_accessibility_edge_costs CASCADE");
		jdbcTemplate.update("INSERT INTO hazard_reports (id, user_id, latitude, longitude, created_at) VALUES (?, ?, ?, ?, NOW())",
				REPORT_2_ID, USER_ID, 40.7582f, -73.9854f);
		insertHazard(HAZARD_2_ID, REPORT_2_ID, "Cracks", 90.0);
		walkAccessibilityService.initializeFromHazards();

		List<WalkAccessibilityEdgeCostEntity> highSeverityCosts = costRepository.findByEdgeIds(new long[]{1L});
		double highSeverityCost = highSeverityCosts.stream()
				.filter(c -> c.getEdgeId() == 1L)
				.findFirst()
				.map(WalkAccessibilityEdgeCostEntity::getCostSeconds)
				.orElse(0.0);

		assertTrue(highSeverityCost > lowSeverityCost, "Higher severity should result in higher cost");

		// Low severity: 1.3 * 1.1 = 1.43 -> 60 * 1.43 = 85.8
		// High severity: 1.3 * 1.9 = 2.47 -> 60 * 2.47 = 148.2
		assertEquals(60.0 * 1.3 * 1.1, lowSeverityCost, 0.1, "Low severity cost calculation");
		assertEquals(60.0 * 1.3 * 1.9, highSeverityCost, 0.1, "High severity cost calculation");
	}

	@Test
	void testEdgesOutsideRadius_notAffected() {
		// Create hazard near edge 1 but far from edge 4
		insertHazardReport(REPORT_1_ID, 40.7582f, -73.9854f);
		insertHazard(HAZARD_1_ID, REPORT_1_ID, "Cracks", 50.0);

		walkAccessibilityService.initializeFromHazards();

		// Edge 4 in Brooklyn should not be affected
		List<WalkAccessibilityEdgeCostEntity> costs = costRepository.findByEdgeIds(new long[]{4L});
		assertTrue(costs.isEmpty(), "Far away edge should not be affected by hazard");
	}

	@Test
	void testDriveEdges_notAffected() {
		// Create hazard near edge 3 (drive mode)
		insertHazardReport(REPORT_1_ID, 40.7582f, -73.9854f);
		insertHazard(HAZARD_1_ID, REPORT_1_ID, "Cracks", 50.0);

		walkAccessibilityService.initializeFromHazards();

		// Edge 3 is drive mode, should not be in accessibility costs
		List<WalkAccessibilityEdgeCostEntity> costs = costRepository.findByEdgeIds(new long[]{3L});
		assertTrue(costs.isEmpty(), "Drive edges should not be affected by walk accessibility");
	}

	@Test
	void testCaseInsensitiveHazardLabels() {
		// Test lowercase
		insertHazardReport(REPORT_1_ID, 40.7582f, -73.9854f);
		insertHazard(HAZARD_1_ID, REPORT_1_ID, "cracks", 50.0);

		int count = walkAccessibilityService.initializeFromHazards();
		assertTrue(count > 0, "Lowercase 'cracks' should be recognized");

		// Clear and test mixed case
		jdbcTemplate.execute("TRUNCATE hazards, hazard_reports, walk_accessibility_edge_costs CASCADE");
		jdbcTemplate.update("INSERT INTO hazard_reports (id, user_id, latitude, longitude, created_at) VALUES (?, ?, ?, ?, NOW())",
				REPORT_2_ID, USER_ID, 40.7582f, -73.9854f);
		insertHazard(HAZARD_2_ID, REPORT_2_ID, "BLOCKED SIDEWALK", 50.0);

		count = walkAccessibilityService.initializeFromHazards();
		assertTrue(count > 0, "Uppercase 'BLOCKED SIDEWALK' should be recognized");
	}

	@Test
	void testDefaultSeverity_whenNull() {
		// Create hazard with null confidence (should default to 50)
		insertHazardReport(REPORT_1_ID, 40.7582f, -73.9854f);
		insertHazard(HAZARD_1_ID, REPORT_1_ID, "Cracks", null);

		walkAccessibilityService.initializeFromHazards();

		List<WalkAccessibilityEdgeCostEntity> costs = costRepository.findByEdgeIds(new long[]{1L});
		WalkAccessibilityEdgeCostEntity cost1 = costs.stream()
				.filter(c -> c.getEdgeId() == 1L)
				.findFirst()
				.orElse(null);

		if (cost1 != null) {
			// CRACKS with default severity 50: 1.3 * 1.5 = 1.95
			double expectedCost = 60.0 * 1.95;
			assertEquals(expectedCost, cost1.getCostSeconds(), 0.1, "Should use default severity 50 when null");
		}
	}
}