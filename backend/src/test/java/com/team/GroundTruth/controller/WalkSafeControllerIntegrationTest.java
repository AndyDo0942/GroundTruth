package com.team.GroundTruth.controller;

import com.team.GroundTruth.entity.maps.WalkSafeEdgeCostEntity;
import com.team.GroundTruth.entity.maps.WalkSafeModifierEntity;
import com.team.GroundTruth.routing.geodata.CrimeFetcher;
import com.team.GroundTruth.routing.geodata.PedestrianCountFetcher;
import com.team.GroundTruth.routing.geodata.StreetlampFetcher;
import com.team.GroundTruth.routing.repo.WalkSafeEdgeCostRepository;
import com.team.GroundTruth.routing.repo.WalkSafeModifierRepository;
import com.team.GroundTruth.routing.service.WalkSafeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.when;

/**
 * Integration test for WalkSafe pipeline that tests the full flow.
 * Uses Testcontainers for a real PostGIS database and mocks external API fetchers.
 * Tests the service layer directly since controller tests require complex auth setup.
 */
@SpringBootTest
@Testcontainers
class WalkSafeControllerIntegrationTest {

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
	private WalkSafeController walkSafeController;

	// Mock external API fetchers so tests don't depend on real APIs
	@MockitoBean
	private PedestrianCountFetcher pedestrianFetcher;

	@MockitoBean
	private StreetlampFetcher streetlampFetcher;

	@MockitoBean
	private CrimeFetcher crimeFetcher;

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

		jdbcTemplate.execute("TRUNCATE walk_safe_modifiers, walk_safe_edge_costs, edges, nodes CASCADE");

		// Insert test nodes (Times Square area)
		jdbcTemplate.execute("""
			INSERT INTO nodes (id, geom) VALUES
			(1, ST_SetSRID(ST_MakePoint(-73.9857, 40.7580), 4326)),
			(2, ST_SetSRID(ST_MakePoint(-73.9850, 40.7585), 4326)),
			(3, ST_SetSRID(ST_MakePoint(-73.9845, 40.7590), 4326))
		""");

		// Insert test edges
		jdbcTemplate.execute("""
			INSERT INTO edges (id, source, target, geom, length_m, cost_s, mode) VALUES
			(1, 1, 2, ST_SetSRID(ST_MakeLine(ST_MakePoint(-73.9857, 40.7580), ST_MakePoint(-73.9850, 40.7585)), 4326), 100.0, 60.0, 'walk'),
			(2, 2, 3, ST_SetSRID(ST_MakeLine(ST_MakePoint(-73.9850, 40.7585), ST_MakePoint(-73.9845, 40.7590)), 4326), 80.0, 48.0, 'walk'),
			(3, 1, 3, ST_SetSRID(ST_MakeLine(ST_MakePoint(-73.9857, 40.7580), ST_MakePoint(-73.9845, 40.7590)), 4326), 150.0, 30.0, 'drive')
		""");
	}

	@Test
	void testControllerInitialize_createsEdgeCosts() {
		var response = walkSafeController.initialize();

		assertEquals(2, response.edgesInitialized());

		List<WalkSafeEdgeCostEntity> costs = costRepository.findByEdgeIds(new long[]{1L, 2L});
		assertEquals(2, costs.size());
	}

	@Test
	void testControllerUpdateCrime_recalculatesCosts() {
		walkSafeController.initialize();

		var request = new com.team.GroundTruth.domain.dto.walksafe.ModifierUpdateRequestDto(
				40.7580, -73.9857, 200.0, 0.8);
		var response = walkSafeController.updateCrimeInArea(request);

		assertTrue(response.edgesUpdated() > 0);

		List<WalkSafeModifierEntity> modifiers = modifierRepository.findByEdgeIds(new long[]{1L});
		assertFalse(modifiers.isEmpty());
		assertEquals(0.8, modifiers.get(0).getCrimeInArea(), 1e-6);

		// base 60 * (1 + 0.8 crime) = 60 * 1.8 = 108
		List<WalkSafeEdgeCostEntity> costs = costRepository.findByEdgeIds(new long[]{1L});
		assertEquals(108.0, costs.get(0).getCostSeconds(), 1e-6);
	}

	@Test
	void testControllerUpdateStreetlight_recalculatesCosts() {
		walkSafeController.initialize();

		var request = new com.team.GroundTruth.domain.dto.walksafe.ModifierUpdateRequestDto(
				40.7580, -73.9857, 200.0, 0.9);
		walkSafeController.updateStreetlight(request);

		List<WalkSafeModifierEntity> modifiers = modifierRepository.findByEdgeIds(new long[]{1L});
		assertEquals(0.9, modifiers.get(0).getStreetlight(), 1e-6);

		// streetlight=0.9: multiplier = min(1.5, 2*(1-0.9)+0.5) = 0.7
		List<WalkSafeEdgeCostEntity> costs = costRepository.findByEdgeIds(new long[]{1L});
		assertEquals(42.0, costs.get(0).getCostSeconds(), 1e-6);
	}

	@Test
	void testControllerUpdatePopDensity_recalculatesCosts() {
		walkSafeController.initialize();

		var request = new com.team.GroundTruth.domain.dto.walksafe.ModifierUpdateRequestDto(
				40.7580, -73.9857, 200.0, 0.8);
		walkSafeController.updatePopDensity(request);

		List<WalkSafeModifierEntity> modifiers = modifierRepository.findByEdgeIds(new long[]{1L});
		assertEquals(0.8, modifiers.get(0).getPopDensity(), 1e-6);

		// popDensity=0.8: multiplier = min(1.5, 2*(1-0.8)+0.3) = 0.7
		List<WalkSafeEdgeCostEntity> costs = costRepository.findByEdgeIds(new long[]{1L});
		assertEquals(42.0, costs.get(0).getCostSeconds(), 1e-6);
	}

	@Test
	void testControllerComputeAll_fetchesFromAPIsAndUpdates() {
		when(pedestrianFetcher.fetch(anyDouble(), anyDouble(), anyDouble())).thenReturn(0.7);
		when(streetlampFetcher.fetch(anyDouble(), anyDouble(), anyDouble())).thenReturn(0.8);
		when(crimeFetcher.fetch(anyDouble(), anyDouble(), anyDouble())).thenReturn(0.3);

		walkSafeController.initialize();

		var request = new com.team.GroundTruth.domain.dto.walksafe.LocationRequestDto(
				40.7580, -73.9857, 200.0);
		walkSafeController.computeAll(request);

		List<WalkSafeModifierEntity> modifiers = modifierRepository.findByEdgeIds(new long[]{1L});
		assertFalse(modifiers.isEmpty());
		assertEquals(0.7, modifiers.get(0).getPopDensity(), 1e-6);
		assertEquals(0.8, modifiers.get(0).getStreetlight(), 1e-6);
		assertEquals(0.3, modifiers.get(0).getCrimeInArea(), 1e-6);

		// popDensity=0.7: 0.9, streetlight=0.8: 0.9, crime=0.3: 1.3
		// Total: 0.9 * 0.9 * 1.3 = 1.053 -> 60 * 1.053 = 63.18
		List<WalkSafeEdgeCostEntity> costs = costRepository.findByEdgeIds(new long[]{1L});
		assertEquals(63.18, costs.get(0).getCostSeconds(), 0.01);
	}

	@Test
	void testControllerComputeAll_safeAreaReducesCost() {
		when(pedestrianFetcher.fetch(anyDouble(), anyDouble(), anyDouble())).thenReturn(1.0);
		when(streetlampFetcher.fetch(anyDouble(), anyDouble(), anyDouble())).thenReturn(1.0);
		when(crimeFetcher.fetch(anyDouble(), anyDouble(), anyDouble())).thenReturn(0.0);

		walkSafeController.initialize();

		var request = new com.team.GroundTruth.domain.dto.walksafe.LocationRequestDto(
				40.7580, -73.9857, 200.0);
		walkSafeController.computeAll(request);

		// Safe area: 0.3 * 0.5 = 0.15 -> 60 * 0.15 = 9
		List<WalkSafeEdgeCostEntity> costs = costRepository.findByEdgeIds(new long[]{1L});
		assertEquals(9.0, costs.get(0).getCostSeconds(), 1e-6);
	}

	@Test
	void testControllerComputeAll_unsafeAreaIncreasesCost() {
		when(pedestrianFetcher.fetch(anyDouble(), anyDouble(), anyDouble())).thenReturn(0.1);
		when(streetlampFetcher.fetch(anyDouble(), anyDouble(), anyDouble())).thenReturn(0.0);
		when(crimeFetcher.fetch(anyDouble(), anyDouble(), anyDouble())).thenReturn(1.0);

		walkSafeController.initialize();

		var request = new com.team.GroundTruth.domain.dto.walksafe.LocationRequestDto(
				40.7580, -73.9857, 200.0);
		walkSafeController.computeAll(request);

		// Unsafe: 1.5 * 1.5 * 2.0 = 4.5 -> 60 * 4.5 = 270
		List<WalkSafeEdgeCostEntity> costs = costRepository.findByEdgeIds(new long[]{1L});
		assertEquals(270.0, costs.get(0).getCostSeconds(), 1e-6);
	}
}