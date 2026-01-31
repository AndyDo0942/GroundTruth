package com.team.GroundTruth.routing.astar;

import com.team.GroundTruth.routing.exception.NoRouteFoundException;
import com.team.GroundTruth.routing.exception.RoutingException;
import com.team.GroundTruth.routing.model.RouteResult;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import org.springframework.stereotype.Component;

/**
 * In-memory A* router for directed walking graphs.
 */
@Component
public class AStarRouter {

	private static final double EARTH_RADIUS_METERS = 6_371_000.0;
	private static final double STALE_EPSILON = 1e-9;
	private static final double DEFAULT_V_MAX_METERS_PER_SECOND = 2.0;

	/**
	 * Routes between two nodes using A*.
	 *
	 * @param startNodeId start node id
	 * @param goalNodeId goal node id
	 * @param nodeCoords coordinates for nodes used in the heuristic
	 * @param outgoingBySource adjacency list keyed by source node id
	 * @param edgeById lookup of edges by id
	 * @return route result
	 * @throws NoRouteFoundException if no path exists in the extracted subgraph
	 * @implNote Uses edge traversal cost in seconds with a haversine/vMax heuristic.
	 */
	public RouteResult route(
			long startNodeId,
			long goalNodeId,
			Map<Long, NodeCoord> nodeCoords,
			Map<Long, List<DirectedEdge>> outgoingBySource,
			Map<Long, DirectedEdge> edgeById
	) {
		return route(startNodeId, goalNodeId, nodeCoords, outgoingBySource, edgeById, DEFAULT_V_MAX_METERS_PER_SECOND);
	}

	/**
	 * Routes between two nodes using A* with a custom heuristic speed bound.
	 *
	 * @param startNodeId start node id
	 * @param goalNodeId goal node id
	 * @param nodeCoords coordinates for nodes used in the heuristic
	 * @param outgoingBySource adjacency list keyed by source node id
	 * @param edgeById lookup of edges by id
	 * @param vMaxMetersPerSecond maximum speed in meters per second used in the heuristic
	 * @return route result
	 * @throws NoRouteFoundException if no path exists in the extracted subgraph
	 */
	public RouteResult route(
			long startNodeId,
			long goalNodeId,
			Map<Long, NodeCoord> nodeCoords,
			Map<Long, List<DirectedEdge>> outgoingBySource,
			Map<Long, DirectedEdge> edgeById,
			double vMaxMetersPerSecond
	) {
		Objects.requireNonNull(nodeCoords, "nodeCoords");
		Objects.requireNonNull(outgoingBySource, "outgoingBySource");
		Objects.requireNonNull(edgeById, "edgeById");

		if (startNodeId == goalNodeId) {
			return new RouteResult(List.of(startNodeId), List.of(), 0.0, 0.0);
		}

		ensureCoordPresent(nodeCoords, startNodeId);
		ensureCoordPresent(nodeCoords, goalNodeId);

		PriorityQueue<QueueEntry> openSet = new PriorityQueue<>(Comparator.comparingDouble(QueueEntry::fScore));
		Map<Long, Double> gScore = new HashMap<>();
		Map<Long, Long> cameFromNode = new HashMap<>();
		Map<Long, Long> cameFromEdge = new HashMap<>();

		gScore.put(startNodeId, 0.0);
		openSet.add(new QueueEntry(
				startNodeId,
				heuristicSeconds(startNodeId, goalNodeId, nodeCoords, vMaxMetersPerSecond),
				0.0
		));

		while (!openSet.isEmpty()) {
			QueueEntry current = openSet.poll();
			Double bestKnown = gScore.get(current.nodeId);
			if (bestKnown == null || current.gScore > bestKnown + STALE_EPSILON) {
				continue;
			}

			if (current.nodeId == goalNodeId) {
				return buildResult(startNodeId, goalNodeId, cameFromNode, cameFromEdge, edgeById, bestKnown);
			}

			for (DirectedEdge edge : outgoingBySource.getOrDefault(current.nodeId, List.of())) {
				double tentative = current.gScore + edge.costSeconds();
				double known = gScore.getOrDefault(edge.targetId(), Double.POSITIVE_INFINITY);
				if (tentative + STALE_EPSILON < known) {
					cameFromNode.put(edge.targetId(), current.nodeId);
					cameFromEdge.put(edge.targetId(), edge.edgeId());
					gScore.put(edge.targetId(), tentative);
					double fScore = tentative + heuristicSeconds(edge.targetId(), goalNodeId, nodeCoords, vMaxMetersPerSecond);
					openSet.add(new QueueEntry(edge.targetId(), fScore, tentative));
				}
			}
		}

		throw new NoRouteFoundException("No route found between nodes " + startNodeId + " and " + goalNodeId + ".");
	}

	private void ensureCoordPresent(Map<Long, NodeCoord> nodeCoords, long nodeId) {
		if (!nodeCoords.containsKey(nodeId)) {
			throw new RoutingException("Missing coordinates for node " + nodeId + ".");
		}
	}

	private RouteResult buildResult(
			long startNodeId,
			long goalNodeId,
			Map<Long, Long> cameFromNode,
			Map<Long, Long> cameFromEdge,
			Map<Long, DirectedEdge> edgeById,
			double durationSeconds
	) {
		List<Long> nodePath = new ArrayList<>();
		List<Long> edgePath = new ArrayList<>();

		long current = goalNodeId;
		nodePath.add(current);
		while (current != startNodeId) {
			Long previous = cameFromNode.get(current);
			Long edgeId = cameFromEdge.get(current);
			if (previous == null || edgeId == null) {
				throw new NoRouteFoundException("No route found between nodes " + startNodeId + " and " + goalNodeId + ".");
			}
			edgePath.add(edgeId);
			nodePath.add(previous);
			current = previous;
		}

		Collections.reverse(nodePath);
		Collections.reverse(edgePath);

		double distanceMeters = 0.0;
		for (Long edgeId : edgePath) {
			DirectedEdge edge = edgeById.get(edgeId);
			if (edge == null) {
				throw new RoutingException("Missing edge data for edge " + edgeId + ".");
			}
			distanceMeters += edge.lengthMeters();
		}

		return new RouteResult(List.copyOf(nodePath), List.copyOf(edgePath), distanceMeters, durationSeconds);
	}

	private double heuristicSeconds(
			long nodeId,
			long goalNodeId,
			Map<Long, NodeCoord> nodeCoords,
			double vMaxMetersPerSecond
	) {
		if (vMaxMetersPerSecond <= 0.0 || Double.isInfinite(vMaxMetersPerSecond)) {
			return 0.0;
		}
		NodeCoord current = nodeCoords.get(nodeId);
		NodeCoord goal = nodeCoords.get(goalNodeId);
		if (current == null || goal == null) {
			throw new RoutingException("Missing coordinates for heuristic computation.");
		}
		double meters = haversineMeters(current, goal);
		return meters / vMaxMetersPerSecond;
	}

	private double haversineMeters(NodeCoord a, NodeCoord b) {
		double lat1 = Math.toRadians(a.lat());
		double lat2 = Math.toRadians(b.lat());
		double dLat = lat2 - lat1;
		double dLon = Math.toRadians(b.lon() - a.lon());

		double sinLat = Math.sin(dLat / 2.0);
		double sinLon = Math.sin(dLon / 2.0);
		double h = sinLat * sinLat + Math.cos(lat1) * Math.cos(lat2) * sinLon * sinLon;
		double c = 2.0 * Math.atan2(Math.sqrt(h), Math.sqrt(1.0 - h));
		return EARTH_RADIUS_METERS * c;
	}

	private record QueueEntry(long nodeId, double fScore, double gScore) {
	}
}
