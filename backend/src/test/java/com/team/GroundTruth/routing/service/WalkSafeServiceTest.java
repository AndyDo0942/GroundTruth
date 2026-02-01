package com.team.GroundTruth.routing.service;

import com.team.GroundTruth.entity.maps.WalkSafeModifierEntity;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

class WalkSafeServiceTest {

	@Test
	void testComputeCost_noModifier_returnsBaseCost() throws Exception {
		double baseCost = 100.0;
		double result = invokeComputeCost(baseCost, null);
		assertEquals(baseCost, result, 1e-6);
	}

	@Test
	void testComputeCost_highPopDensity_lowersCost() throws Exception {
		double baseCost = 100.0;
		WalkSafeModifierEntity modifier = new WalkSafeModifierEntity(1L);
		modifier.setPopDensity(1.0); // Max density = safest

		double result = invokeComputeCost(baseCost, modifier);

		// With popDensity=1.0: multiplier = min(1.5, 2*(1-1)+0.3) = min(1.5, 0.3) = 0.3
		assertEquals(30.0, result, 1e-6);
	}

	@Test
	void testComputeCost_lowPopDensity_increasesCost() throws Exception {
		double baseCost = 100.0;
		WalkSafeModifierEntity modifier = new WalkSafeModifierEntity(1L);
		modifier.setPopDensity(0.1); // Low density = less safe

		double result = invokeComputeCost(baseCost, modifier);

		// With popDensity=0.1: multiplier = min(1.5, 2*(1-0.1)+0.3) = min(1.5, 2.1) = 1.5
		assertEquals(150.0, result, 1e-6);
	}

	@Test
	void testComputeCost_highStreetlight_lowersCost() throws Exception {
		double baseCost = 100.0;
		WalkSafeModifierEntity modifier = new WalkSafeModifierEntity(1L);
		modifier.setStreetlight(1.0); // Max streetlight = safest

		double result = invokeComputeCost(baseCost, modifier);

		// With streetlight=1.0: multiplier = min(1.5, 2*(1-1)+0.5) = min(1.5, 0.5) = 0.5
		assertEquals(50.0, result, 1e-6);
	}

	@Test
	void testComputeCost_noStreetlight_increasesCost() throws Exception {
		double baseCost = 100.0;
		WalkSafeModifierEntity modifier = new WalkSafeModifierEntity(1L);
		modifier.setStreetlight(0.0); // No streetlight = less safe

		double result = invokeComputeCost(baseCost, modifier);

		// With streetlight=0.0: multiplier = min(1.5, 2*(1-0)+0.5) = min(1.5, 2.5) = 1.5
		assertEquals(150.0, result, 1e-6);
	}

	@Test
	void testComputeCost_highCrime_increasesCost() throws Exception {
		double baseCost = 100.0;
		WalkSafeModifierEntity modifier = new WalkSafeModifierEntity(1L);
		modifier.setCrimeInArea(1.0); // Max crime

		double result = invokeComputeCost(baseCost, modifier);

		// With crimeInArea=1.0: multiplier = 1.0 + 1.0 = 2.0
		assertEquals(200.0, result, 1e-6);
	}

	@Test
	void testComputeCost_noCrime_noChange() throws Exception {
		double baseCost = 100.0;
		WalkSafeModifierEntity modifier = new WalkSafeModifierEntity(1L);
		modifier.setCrimeInArea(0.0); // No crime

		double result = invokeComputeCost(baseCost, modifier);

		// With crimeInArea=0.0: no effect (condition is > 0)
		assertEquals(100.0, result, 1e-6);
	}

	@Test
	void testComputeCost_allFactorsCombined_multipliesCorrectly() throws Exception {
		double baseCost = 100.0;
		WalkSafeModifierEntity modifier = new WalkSafeModifierEntity(1L);
		modifier.setPopDensity(0.5);    // mid density
		modifier.setStreetlight(0.5);   // mid streetlight
		modifier.setCrimeInArea(0.5);   // mid crime

		double result = invokeComputeCost(baseCost, modifier);

		// popDensity=0.5: multiplier = min(1.5, 2*(1-0.5)+0.3) = min(1.5, 1.3) = 1.3
		// streetlight=0.5: multiplier *= min(1.5, 2*(1-0.5)+0.5) = min(1.5, 1.5) = 1.5
		// crimeInArea=0.5: multiplier *= 1.0 + 0.5 = 1.5
		// Total: 1.3 * 1.5 * 1.5 = 2.925
		assertEquals(292.5, result, 1e-6);
	}

	@Test
	void testComputeCost_safestPossible() throws Exception {
		double baseCost = 100.0;
		WalkSafeModifierEntity modifier = new WalkSafeModifierEntity(1L);
		modifier.setPopDensity(1.0);    // max density (safest)
		modifier.setStreetlight(1.0);   // max streetlight (safest)
		modifier.setCrimeInArea(0.0);   // no crime (safest)

		double result = invokeComputeCost(baseCost, modifier);

		// popDensity=1.0: 0.3
		// streetlight=1.0: 0.5
		// crimeInArea=0.0: no effect
		// Total: 0.3 * 0.5 = 0.15
		assertEquals(15.0, result, 1e-6);
	}

	@Test
	void testComputeCost_leastSafe() throws Exception {
		double baseCost = 100.0;
		WalkSafeModifierEntity modifier = new WalkSafeModifierEntity(1L);
		modifier.setPopDensity(0.01);   // near-zero density (triggers condition, capped at 1.5)
		modifier.setStreetlight(0.0);   // no streetlight (capped at 1.5)
		modifier.setCrimeInArea(1.0);   // max crime

		double result = invokeComputeCost(baseCost, modifier);

		// popDensity=0.01: min(1.5, 2*(1-0.01)+0.3) = min(1.5, 2.28) = 1.5
		// streetlight=0.0: min(1.5, 2*(1-0)+0.5) = min(1.5, 2.5) = 1.5
		// crimeInArea=1.0: 1.0 + 1.0 = 2.0
		// Total: 1.5 * 1.5 * 2.0 = 4.5
		assertEquals(450.0, result, 1e-6);
	}

	@Test
	void testComputeCost_neverReturnsNegative() throws Exception {
		double baseCost = 0.0;
		WalkSafeModifierEntity modifier = new WalkSafeModifierEntity(1L);
		modifier.setPopDensity(1.0);
		modifier.setStreetlight(1.0);
		modifier.setCrimeInArea(0.0);

		double result = invokeComputeCost(baseCost, modifier);

		assertTrue(result >= 0.0);
		assertEquals(0.0, result, 1e-6);
	}

	/**
	 * Uses reflection to call the private computeCost method.
	 */
	private double invokeComputeCost(double baseCost, WalkSafeModifierEntity modifier) throws Exception {
		// Create a minimal service instance with null repos (not used by computeCost)
		WalkSafeServiceImpl service = createServiceWithNullRepos();

		Method method = WalkSafeServiceImpl.class.getDeclaredMethod(
				"computeCost", double.class, WalkSafeModifierEntity.class);
		method.setAccessible(true);
		return (double) method.invoke(service, baseCost, modifier);
	}

	/**
	 * Creates a service instance bypassing null checks for testing private methods.
	 */
	private WalkSafeServiceImpl createServiceWithNullRepos() throws Exception {
		java.lang.reflect.Constructor<WalkSafeServiceImpl> constructor =
				WalkSafeServiceImpl.class.getDeclaredConstructor(
						com.team.GroundTruth.routing.repo.EdgeRepository.class,
						com.team.GroundTruth.routing.repo.WalkSafeModifierRepository.class,
						com.team.GroundTruth.routing.repo.WalkSafeEdgeCostRepository.class
				);

		// Use mock objects to satisfy non-null checks
		return new WalkSafeServiceImpl(
				org.mockito.Mockito.mock(com.team.GroundTruth.routing.repo.EdgeRepository.class),
				org.mockito.Mockito.mock(com.team.GroundTruth.routing.repo.WalkSafeModifierRepository.class),
				org.mockito.Mockito.mock(com.team.GroundTruth.routing.repo.WalkSafeEdgeCostRepository.class)
		);
	}
}