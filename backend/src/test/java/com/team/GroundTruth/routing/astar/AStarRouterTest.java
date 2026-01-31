package com.team.GroundTruth.routing.astar;

import com.team.GroundTruth.routing.exception.NoRouteFoundException;
import com.team.GroundTruth.routing.model.RouteResult;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AStarRouterTest {

	@Test
	void testChoosesLowestDurationPath() {
		AStarRouter router = new AStarRouter();
		Map<Long, NodeCoord> coords = new HashMap<>();
		coords.put(1L, new NodeCoord(0.0, 0.0));
		coords.put(2L, new NodeCoord(0.0, 0.001));
		coords.put(3L, new NodeCoord(0.0, 0.002));

		Map<Long, List<DirectedEdge>> outgoing = new HashMap<>();
		Map<Long, DirectedEdge> edgeById = new HashMap<>();
		addEdge(outgoing, edgeById, 1L, new DirectedEdge(10L, 2L, 100.0, 20.0));
		addEdge(outgoing, edgeById, 2L, new DirectedEdge(11L, 3L, 100.0, 20.0));
		addEdge(outgoing, edgeById, 1L, new DirectedEdge(12L, 3L, 200.0, 60.0));

		RouteResult result = router.route(1L, 3L, coords, outgoing, edgeById);

		assertEquals(List.of(1L, 2L, 3L), result.pathNodeIds());
		assertEquals(List.of(10L, 11L), result.pathEdgeIds());
		assertEquals(200.0, result.distanceMeters(), 1e-6);
		assertEquals(40.0, result.durationSeconds(), 1e-6);
	}

	@Test
	void testStartEqualsGoal() {
		AStarRouter router = new AStarRouter();
		Map<Long, NodeCoord> coords = Map.of(1L, new NodeCoord(0.0, 0.0));
		RouteResult result = router.route(1L, 1L, coords, Map.of(), Map.of());

		assertEquals(List.of(1L), result.pathNodeIds());
		assertEquals(List.of(), result.pathEdgeIds());
		assertEquals(0.0, result.distanceMeters(), 1e-6);
		assertEquals(0.0, result.durationSeconds(), 1e-6);
	}

	@Test
	void testNoPathThrows() {
		AStarRouter router = new AStarRouter();
		Map<Long, NodeCoord> coords = Map.of(
				1L, new NodeCoord(0.0, 0.0),
				2L, new NodeCoord(0.0, 0.001)
		);

		assertThrows(NoRouteFoundException.class, () ->
				router.route(1L, 2L, coords, Map.of(), Map.of())
		);
	}

	@Test
	void testHeuristicAdmissibleDoesNotBreakOptimality() {
		Map<Long, NodeCoord> coords = Map.of(
				1L, new NodeCoord(0.0, 0.0),
				2L, new NodeCoord(0.0, 0.001),
				3L, new NodeCoord(0.001, 0.0),
				4L, new NodeCoord(0.001, 0.001)
		);

		Map<Long, List<DirectedEdge>> outgoing = new HashMap<>();
		Map<Long, DirectedEdge> edgeById = new HashMap<>();
		addEdge(outgoing, edgeById, 1L, new DirectedEdge(1L, 2L, 100.0, 10.0));
		addEdge(outgoing, edgeById, 2L, new DirectedEdge(2L, 4L, 100.0, 10.0));
		addEdge(outgoing, edgeById, 1L, new DirectedEdge(3L, 3L, 50.0, 5.0));
		addEdge(outgoing, edgeById, 3L, new DirectedEdge(4L, 4L, 300.0, 30.0));

		AStarRouter router = new AStarRouter();

		RouteResult baseline = router.route(1L, 4L, coords, outgoing, edgeById, Double.POSITIVE_INFINITY);
		RouteResult result = router.route(1L, 4L, coords, outgoing, edgeById);

		assertEquals(baseline.durationSeconds(), result.durationSeconds(), 1e-6);
		assertEquals(baseline.distanceMeters(), result.distanceMeters(), 1e-6);
		assertEquals(baseline.pathEdgeIds(), result.pathEdgeIds());
	}

	private static void addEdge(
			Map<Long, List<DirectedEdge>> outgoing,
			Map<Long, DirectedEdge> edgeById,
			long source,
			DirectedEdge edge
	) {
		outgoing.computeIfAbsent(source, key -> new java.util.ArrayList<>()).add(edge);
		edgeById.put(edge.edgeId(), edge);
	}
}
