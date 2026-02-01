package com.team.GroundTruth.demo;

import com.team.GroundTruth.routing.service.WalkSafeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Seeds randomized walk-safe modifiers near Brown University for demo purposes.
 */
@Component
@ConditionalOnProperty(value = "demo.brown-hazards.enabled", havingValue = "true")
public class BrownWalkSafeHazardSeeder implements ApplicationRunner {

	private static final Logger LOG = LoggerFactory.getLogger(BrownWalkSafeHazardSeeder.class);

	private static final double BROWN_LAT = 41.8268;
	private static final double BROWN_LON = -71.4025;
	private static final double RADIUS_METERS = 900.0;
	private static final double SEED_FRACTION = 0.35;

	private final JdbcTemplate jdbcTemplate;
	private final WalkSafeService walkSafeService;

	public BrownWalkSafeHazardSeeder(JdbcTemplate jdbcTemplate, WalkSafeService walkSafeService) {
		this.jdbcTemplate = jdbcTemplate;
		this.walkSafeService = walkSafeService;
	}

	@Override
	public void run(ApplicationArguments args) {
		String seedSql = """
			INSERT INTO walk_safe_modifiers (edge_id, pop_density, streetlight, crime_in_area, updated_at)
			SELECT e.id,
			       (random() * 0.3) + 0.05,
			       (random() * 0.3) + 0.1,
			       0.6 + (random() * 0.4),
			       NOW()
			FROM edges e
			WHERE e.mode = 'walk'
			  AND ST_DWithin(
			      ST_Centroid(e.geom)::geography,
			      ST_SetSRID(ST_MakePoint(%f, %f), 4326)::geography,
			      %f
			  )
			  AND random() < %f
			ON CONFLICT (edge_id) DO UPDATE
			SET pop_density = EXCLUDED.pop_density,
			    streetlight = EXCLUDED.streetlight,
			    crime_in_area = EXCLUDED.crime_in_area,
			    updated_at = NOW()
		""".formatted(BROWN_LON, BROWN_LAT, RADIUS_METERS, SEED_FRACTION);

		int affected = jdbcTemplate.update(seedSql);
		int recomputed = walkSafeService.initializeEdgeCosts();

		LOG.info("Seeded {} demo walk-safe modifiers near Brown University; recomputed {} walk-safe edge costs.",
				affected, recomputed);
	}
}
