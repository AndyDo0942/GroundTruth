package com.team.GroundTruth.routing.service;

import com.team.GroundTruth.entity.maps.WalkSafeEdgeCostEntity;
import com.team.GroundTruth.entity.maps.WalkSafeModifierEntity;
import com.team.GroundTruth.routing.repo.EdgeRepository;
import com.team.GroundTruth.routing.repo.WalkSafeEdgeCostRepository;
import com.team.GroundTruth.routing.repo.WalkSafeModifierRepository;
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

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
class WalkSafePipelineIntegrationTest {

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
	}

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private WalkSafeService walkSafeService;

	@Autowired
	private WalkSafeEdgeCostRepository costRepository;

	@Autowired
	private WalkSafeModifierRepository modifierRepository;

	@Autowired
	private EdgeRepository edgeRepository;

	@BeforeEach
	void setUp() {
		// Create schema
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
			CREATE TABLE IF NOT EXISTS walk_safe_edge_costs (
				edge_id BIGINT PRIMARY KEY REFERENCES edges(id) ON DELETE CASCADE,
				cost_seconds DOUBLE PRECISION NOT NULL,
				updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
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

		// Clear data between tests
		jdbcTemplate.execute("TRUNCATE walk_safe_modifiers, walk_safe_edge_costs, edges, nodes CASCADE");

		// Insert test nodes (NYC area)
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

		// Insert test edges (walk mode)
		jdbcTemplate.execute("""
			INSERT INTO edges (id, source, target, geom, length_m, cost_s, mode) VALUES
			(1, 1, 2, ST_SetSRID(ST_MakeLine(ST_MakePoint(-73.9857, 40.7580), ST_MakePoint(-73.9850, 40.7585)), 4326), 100.0, 60.0, 'walk')
		""");
		jdbcTemplate.execute("""
			INSERT INTO edges (id, source, target, geom, length_m, cost_s, mode) VALUES
			(2, 2, 3, ST_SetSRID(ST_MakeLine(ST_MakePoint(-73.9850, 40.7585), ST_MakePoint(-73.9845, 40.7590)), 4326), 80.0, 48.0, 'walk')
		""");
		// Edge 3: drive mode (should be ignored by walk safe service)
		jdbcTemplate.execute("""
			INSERT INTO edges (id, source, target, geom, length_m, cost_s, mode) VALUES
			(3, 1, 3, ST_SetSRID(ST_MakeLine(ST_MakePoint(-73.9857, 40.7580), ST_MakePoint(-73.9845, 40.7590)), 4326), 150.0, 30.0, 'drive')
		""");
	}

	@Test
	void testInitializeEdgeCosts_createsEntriesForWalkEdgesOnly() {
		int count = walkSafeService.initializeEdgeCosts();

		assertEquals(2, count, "Should initialize 2 walk edges");

		List<WalkSafeEdgeCostEntity> costs = costRepository.findByEdgeIds(new long[]{1L, 2L, 3L});
		assertEquals(2, costs.size());

		// Without modifiers, costs should equal base costs
		WalkSafeEdgeCostEntity cost1 = costs.stream().filter(c -> c.getEdgeId() == 1L).findFirst().orElseThrow();
		WalkSafeEdgeCostEntity cost2 = costs.stream().filter(c -> c.getEdgeId() == 2L).findFirst().orElseThrow();

		assertEquals(60.0, cost1.getCostSeconds(), 1e-6, "Edge 1 should have base cost 60s");
		assertEquals(48.0, cost2.getCostSeconds(), 1e-6, "Edge 2 should have base cost 48s");
	}

	@Test
	void testUpdateModifiers_recalculatesCosts() {
		// First initialize
		walkSafeService.initializeEdgeCosts();

		// Update crime in the area covering edge 1 (centered on node 1)
		int updated = walkSafeService.updateCrimeInArea(-73.9857, 40.7580, 50.0, 0.5);

		assertTrue(updated > 0, "Should update at least one edge");

		// Check that modifier was saved
		List<WalkSafeModifierEntity> modifiers = modifierRepository.findByEdgeIds(new long[]{1L});
		assertFalse(modifiers.isEmpty(), "Should have modifier for edge 1");
		assertEquals(0.5, modifiers.get(0).getCrimeInArea(), 1e-6);

		// Check that cost was recalculated
		// With crime=0.5, multiplier = 1 + 0.5 = 1.5
		// New cost = 60 * 1.5 = 90
		List<WalkSafeEdgeCostEntity> costs = costRepository.findByEdgeIds(new long[]{1L});
		assertEquals(90.0, costs.get(0).getCostSeconds(), 1e-6, "Cost should be 60 * 1.5 = 90");
	}

	@Test
	void testUpdateMultipleModifiers_stacksMultipliers() {
		walkSafeService.initializeEdgeCosts();

		// Update all three modifiers for edge 1
		walkSafeService.updateCrimeInArea(-73.9857, 40.7580, 50.0, 0.5);      // 1.5x
		walkSafeService.updateStreetlight(-73.9857, 40.7580, 50.0, 0.5);      // 1.5x
		walkSafeService.updatePopDensity(-73.9857, 40.7580, 50.0, 0.5);       // 1.3x

		// Total multiplier: 1.5 * 1.5 * 1.3 = 2.925
		// New cost = 60 * 2.925 = 175.5
		List<WalkSafeEdgeCostEntity> costs = costRepository.findByEdgeIds(new long[]{1L});
		assertEquals(175.5, costs.get(0).getCostSeconds(), 1e-6);
	}

	@Test
	void testSafeArea_reducesCost() {
		walkSafeService.initializeEdgeCosts();

		// Safe area: high density, good lighting, low crime
		walkSafeService.updatePopDensity(-73.9857, 40.7580, 50.0, 1.0);       // 0.3x
		walkSafeService.updateStreetlight(-73.9857, 40.7580, 50.0, 1.0);      // 0.5x

		// Total multiplier: 0.3 * 0.5 = 0.15
		// New cost = 60 * 0.15 = 9
		List<WalkSafeEdgeCostEntity> costs = costRepository.findByEdgeIds(new long[]{1L});
		assertEquals(9.0, costs.get(0).getCostSeconds(), 1e-6);
	}

	@Test
	void testUnsafeArea_increasesCost() {
		walkSafeService.initializeEdgeCosts();

		// Unsafe area: low density, poor lighting, high crime
		walkSafeService.updatePopDensity(-73.9857, 40.7580, 50.0, 0.1);       // 1.5x (capped)
		walkSafeService.updateStreetlight(-73.9857, 40.7580, 50.0, 0.0);      // 1.5x (capped)
		walkSafeService.updateCrimeInArea(-73.9857, 40.7580, 50.0, 1.0);      // 2.0x

		// Total multiplier: 1.5 * 1.5 * 2.0 = 4.5
		// New cost = 60 * 4.5 = 270
		List<WalkSafeEdgeCostEntity> costs = costRepository.findByEdgeIds(new long[]{1L});
		assertEquals(270.0, costs.get(0).getCostSeconds(), 1e-6);
	}

	@Test
	void testEdgesOutsideRadius_notAffected() {
		walkSafeService.initializeEdgeCosts();

		// Update with very small radius that won't include edge 2
		walkSafeService.updateCrimeInArea(-73.9857, 40.7580, 10.0, 1.0);

		// Edge 2 should still have base cost
		List<WalkSafeEdgeCostEntity> costs = costRepository.findByEdgeIds(new long[]{2L});
		assertEquals(48.0, costs.get(0).getCostSeconds(), 1e-6, "Edge 2 should be unaffected");
	}
}