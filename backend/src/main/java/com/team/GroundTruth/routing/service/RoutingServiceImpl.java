package com.team.GroundTruth.routing.service;

import com.team.GroundTruth.entity.maps.EdgeEntity;
import com.team.GroundTruth.entity.maps.NodeEntity;
import com.team.GroundTruth.entity.maps.WalkAccessibilityEdgeCostEntity;
import com.team.GroundTruth.entity.maps.WalkSafeEdgeCostEntity;
import com.team.GroundTruth.routing.astar.AStarRouter;
import com.team.GroundTruth.routing.astar.DirectedEdge;
import com.team.GroundTruth.routing.astar.NodeCoord;
import com.team.GroundTruth.routing.exception.NoRouteFoundException;
import com.team.GroundTruth.routing.exception.NodeSnapException;
import com.team.GroundTruth.routing.exception.RoutingException;
import com.team.GroundTruth.routing.model.Location;
import com.team.GroundTruth.routing.model.RouteResult;
import com.team.GroundTruth.routing.model.RouteType;
import com.team.GroundTruth.routing.model.TravelMode;
import com.team.GroundTruth.routing.repo.EdgeCostOverlayRepository;
import com.team.GroundTruth.routing.repo.EdgeRepository;
import com.team.GroundTruth.routing.repo.NodeRepository;
import com.team.GroundTruth.routing.repo.WalkAccessibilityEdgeCostRepository;
import com.team.GroundTruth.routing.repo.WalkSafeEdgeCostRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default routing service implementation that orchestrates database access and A* search.
 */
@Service
@Transactional(readOnly = true)
public class RoutingServiceImpl implements RoutingService {

	private static final double EARTH_RADIUS_METERS = 6_371_000.0;
	private static final double DEFAULT_RADIUS_MULTIPLIER = 1.2;
	private static final double MIN_RADIUS_METERS = 2_000.0;

	private final NodeRepository nodeRepository;
	private final EdgeRepository edgeRepository;
	private final EdgeCostOverlayRepository edgeCostOverlayRepository;
	private final WalkSafeEdgeCostRepository walkSafeEdgeCostRepository;
	private final WalkAccessibilityEdgeCostRepository walkAccessibilityEdgeCostRepository;
	private final AStarRouter aStarRouter;

	/**
	 * Creates a routing service implementation.
	 *
	 * @param nodeRepository repository used for node queries
	 * @param edgeRepository repository used for edge queries
	 * @param edgeCostOverlayRepository repository used for overlay queries
	 * @param walkSafeEdgeCostRepository repository for walk safety costs
	 * @param walkAccessibilityEdgeCostRepository repository for walk accessibility costs
	 * @param aStarRouter A* router implementation
	 */
	public RoutingServiceImpl(
			NodeRepository nodeRepository,
			EdgeRepository edgeRepository,
			EdgeCostOverlayRepository edgeCostOverlayRepository,
			WalkSafeEdgeCostRepository walkSafeEdgeCostRepository,
			WalkAccessibilityEdgeCostRepository walkAccessibilityEdgeCostRepository,
			AStarRouter aStarRouter
	) {
		this.nodeRepository = Objects.requireNonNull(nodeRepository, "nodeRepository");
		this.edgeRepository = Objects.requireNonNull(edgeRepository, "edgeRepository");
		this.edgeCostOverlayRepository = Objects.requireNonNull(edgeCostOverlayRepository, "edgeCostOverlayRepository");
		this.walkSafeEdgeCostRepository = Objects.requireNonNull(walkSafeEdgeCostRepository, "walkSafeEdgeCostRepository");
		this.walkAccessibilityEdgeCostRepository = Objects.requireNonNull(walkAccessibilityEdgeCostRepository, "walkAccessibilityEdgeCostRepository");
		this.aStarRouter = Objects.requireNonNull(aStarRouter, "aStarRouter");
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public RouteResult route(Location start, Location end, double radiusMeters) {
		return routeWalking(start, end, radiusMeters);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public RouteResult route(Location start, Location end, double radiusMeters, TravelMode mode) {
		Objects.requireNonNull(start, "start");
		Objects.requireNonNull(end, "end");
		Objects.requireNonNull(mode, "mode");

		NodeEntity startNode = nodeRepository.snapNearestNode(start.lat(), start.lon())
				.orElseThrow(() -> new NodeSnapException("Unable to snap start coordinate to a graph node."));
		NodeEntity endNode = nodeRepository.snapNearestNode(end.lat(), end.lon())
				.orElseThrow(() -> new NodeSnapException("Unable to snap end coordinate to a graph node."));

		long startNodeId = startNode.getId();
		long endNodeId = endNode.getId();

		if (startNodeId == endNodeId) {
			return new RouteResult(List.of(startNodeId), List.of(), 0.0, 0.0);
		}

		double baseRadius = radiusMeters > 0.0 ? radiusMeters : defaultRadiusMeters(start, end);
		double[] attempts = new double[]{baseRadius, baseRadius * 2.0, baseRadius * 4.0};
		NoRouteFoundException last = null;
		for (double radius : attempts) {
			try {
				return routeWithRadius(start, end, startNode, endNode, radius, mode);
			} catch (NoRouteFoundException ex) {
				last = ex;
			}
		}
		throw last == null
				? new NoRouteFoundException("No route found between start and end coordinates.")
				: last;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public RouteResult routeWalking(Location start, Location end, double radiusMeters) {
		return route(start, end, radiusMeters, TravelMode.WALK);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public RouteResult routeDriving(Location start, Location end, double radiusMeters) {
		return route(start, end, radiusMeters, TravelMode.DRIVE);
	}

	private NodeCoord toCoord(NodeEntity node) {
		Point geom = node.getGeom();
		if (geom == null) {
			throw new RoutingException("Node geometry is missing for node " + node.getId() + ".");
		}
		return new NodeCoord(geom.getY(), geom.getX());
	}

	private RouteResult routeWithRadius(
			Location start,
			Location end,
			NodeEntity startNode,
			NodeEntity endNode,
			double radiusMeters,
			TravelMode mode
	) {
		List<NodeEntity> subgraphNodes = nodeRepository.loadSubgraphNodes(
				start.lat(),
				start.lon(),
				end.lat(),
				end.lon(),
				radiusMeters
		);

		Map<Long, NodeCoord> nodeCoords = new HashMap<>();
		for (NodeEntity node : subgraphNodes) {
			nodeCoords.put(node.getId(), toCoord(node));
		}
		nodeCoords.putIfAbsent(startNode.getId(), toCoord(startNode));
		nodeCoords.putIfAbsent(endNode.getId(), toCoord(endNode));

		long[] nodeIds = nodeCoords.keySet().stream().mapToLong(Long::longValue).toArray();
		List<EdgeEntity> subgraphEdges = edgeRepository.loadSubgraphEdgesByMode(nodeIds, mode.dbValue());

		Map<Long, OverlayAccumulator> overlayByEdgeId = loadOverlays(subgraphEdges, mode);

		Map<Long, List<DirectedEdge>> outgoingBySource = new HashMap<>();
		Map<Long, DirectedEdge> edgeById = new HashMap<>();
		for (EdgeEntity edge : subgraphEdges) {
			OverlayAccumulator overlay = overlayByEdgeId.get(edge.getId());
			double costSeconds = edge.getCostSeconds();
			if (overlay != null) {
				costSeconds = Math.max(0.0, costSeconds * overlay.multiplier + overlay.deltaSeconds);
			}
			DirectedEdge directed = new DirectedEdge(
					edge.getId(),
					edge.getTarget(),
					edge.getLengthMeters(),
					costSeconds
			);
			outgoingBySource.computeIfAbsent(edge.getSource(), key -> new ArrayList<>()).add(directed);
			edgeById.put(edge.getId(), directed);
		}

		if (outgoingBySource.isEmpty()) {
			throw new NoRouteFoundException("No edges available for mode " + mode + ".");
		}

		return aStarRouter.route(startNode.getId(), endNode.getId(), nodeCoords, outgoingBySource, edgeById);
	}

	private Map<Long, OverlayAccumulator> loadOverlays(List<EdgeEntity> edges, TravelMode mode) {
		if (edges.isEmpty()) {
			return Map.of();
		}
		long[] edgeIds = edges.stream().mapToLong(EdgeEntity::getId).toArray();
		Map<Long, OverlayAccumulator> overlayByEdgeId = new HashMap<>();
		OffsetDateTime asOf = OffsetDateTime.now(ZoneOffset.UTC);
		edgeCostOverlayRepository.findActiveOverlays(edgeIds, mode.dbValue(), asOf)
				.forEach(overlay -> overlayByEdgeId
						.computeIfAbsent(overlay.getEdgeId(), key -> new OverlayAccumulator())
						.add(overlay.getCostMultiplier(), overlay.getCostDeltaSeconds()));
		return overlayByEdgeId;
	}

	private double defaultRadiusMeters(Location start, Location end) {
		double distance = haversineMeters(start.lat(), start.lon(), end.lat(), end.lon());
		return Math.max(MIN_RADIUS_METERS, distance * DEFAULT_RADIUS_MULTIPLIER);
	}

	private double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
		double radLat1 = Math.toRadians(lat1);
		double radLat2 = Math.toRadians(lat2);
		double dLat = radLat2 - radLat1;
		double dLon = Math.toRadians(lon2 - lon1);

		double sinLat = Math.sin(dLat / 2.0);
		double sinLon = Math.sin(dLon / 2.0);
		double h = sinLat * sinLat + Math.cos(radLat1) * Math.cos(radLat2) * sinLon * sinLon;
		double c = 2.0 * Math.atan2(Math.sqrt(h), Math.sqrt(1.0 - h));
		return EARTH_RADIUS_METERS * c;
	}


	private static final class OverlayAccumulator {
		private double multiplier = 1.0;
		private double deltaSeconds = 0.0;

		private void add(double multiplier, double deltaSeconds) {
			this.multiplier *= multiplier;
			this.deltaSeconds += deltaSeconds;
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public RouteResult route(Location start, Location end, double radiusMeters, RouteType routeType) {
		Objects.requireNonNull(start, "start");
		Objects.requireNonNull(end, "end");
		Objects.requireNonNull(routeType, "routeType");

		NodeEntity startNode = nodeRepository.snapNearestNode(start.lat(), start.lon())
				.orElseThrow(() -> new NodeSnapException("Unable to snap start coordinate to a graph node."));
		NodeEntity endNode = nodeRepository.snapNearestNode(end.lat(), end.lon())
				.orElseThrow(() -> new NodeSnapException("Unable to snap end coordinate to a graph node."));

		long startNodeId = startNode.getId();
		long endNodeId = endNode.getId();

		if (startNodeId == endNodeId) {
			return new RouteResult(List.of(startNodeId), List.of(), 0.0, 0.0);
		}

		double baseRadius = radiusMeters > 0.0 ? radiusMeters : defaultRadiusMeters(start, end);
		double[] attempts = new double[]{baseRadius, baseRadius * 2.0, baseRadius * 4.0};
		NoRouteFoundException last = null;
		for (double radius : attempts) {
			try {
				return routeWithRadiusAndType(start, end, startNode, endNode, radius, routeType);
			} catch (NoRouteFoundException ex) {
				last = ex;
			}
		}
		throw last == null
				? new NoRouteFoundException("No route found between start and end coordinates.")
				: last;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public RouteResult routeWalkingSafe(Location start, Location end, double radiusMeters) {
		return route(start, end, radiusMeters, RouteType.WALK_SAFE);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public RouteResult routeWalkingAccessible(Location start, Location end, double radiusMeters) {
		return route(start, end, radiusMeters, RouteType.WALK_ACCESSIBLE);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public RouteResult routeWalkingSafeAccessible(Location start, Location end, double radiusMeters) {
		return route(start, end, radiusMeters, RouteType.WALK_SAFE_ACCESSIBLE);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public RouteResult routeDrivingSafe(Location start, Location end, double radiusMeters) {
		return route(start, end, radiusMeters, RouteType.DRIVE_SAFE);
	}

	/**
	 * Routes using a specific RouteType which determines cost strategy.
	 */
	private RouteResult routeWithRadiusAndType(
			Location start,
			Location end,
			NodeEntity startNode,
			NodeEntity endNode,
			double radiusMeters,
			RouteType routeType
	) {
		TravelMode mode = routeType.travelMode();

		List<NodeEntity> subgraphNodes = nodeRepository.loadSubgraphNodes(
				start.lat(),
				start.lon(),
				end.lat(),
				end.lon(),
				radiusMeters
		);

		Map<Long, NodeCoord> nodeCoords = new HashMap<>();
		for (NodeEntity node : subgraphNodes) {
			nodeCoords.put(node.getId(), toCoord(node));
		}
		nodeCoords.putIfAbsent(startNode.getId(), toCoord(startNode));
		nodeCoords.putIfAbsent(endNode.getId(), toCoord(endNode));

		long[] nodeIds = nodeCoords.keySet().stream().mapToLong(Long::longValue).toArray();
		List<EdgeEntity> subgraphEdges = edgeRepository.loadSubgraphEdgesByMode(nodeIds, mode.dbValue());

		// Load base overlays
		Map<Long, OverlayAccumulator> overlayByEdgeId = loadOverlays(subgraphEdges, mode);

		// Load pre-computed costs based on route type
		long[] edgeIds = subgraphEdges.stream().mapToLong(EdgeEntity::getId).toArray();
		Map<Long, Double> precomputedCosts = loadPrecomputedCosts(edgeIds, routeType);

		Map<Long, List<DirectedEdge>> outgoingBySource = new HashMap<>();
		Map<Long, DirectedEdge> edgeById = new HashMap<>();
		for (EdgeEntity edge : subgraphEdges) {
			double costSeconds;

			// Check if we have a pre-computed cost for this edge
			Double precomputed = precomputedCosts.get(edge.getId());
			if (precomputed != null) {
				costSeconds = precomputed;
			} else {
				// Fall back to base cost
				costSeconds = edge.getCostSeconds();
			}

			// Apply any temporary overlays on top
			OverlayAccumulator overlay = overlayByEdgeId.get(edge.getId());
			if (overlay != null) {
				costSeconds = Math.max(0.0, costSeconds * overlay.multiplier + overlay.deltaSeconds);
			}

			DirectedEdge directed = new DirectedEdge(
					edge.getId(),
					edge.getTarget(),
					edge.getLengthMeters(),
					costSeconds
			);
			outgoingBySource.computeIfAbsent(edge.getSource(), key -> new ArrayList<>()).add(directed);
			edgeById.put(edge.getId(), directed);
		}

		if (outgoingBySource.isEmpty()) {
			throw new NoRouteFoundException("No edges available for mode " + mode + ".");
		}

		return aStarRouter.route(startNode.getId(), endNode.getId(), nodeCoords, outgoingBySource, edgeById);
	}

	/**
	 * Loads pre-computed costs based on route type.
	 * Combines costs from multiple tables if route type requires it.
	 */
	private Map<Long, Double> loadPrecomputedCosts(long[] edgeIds, RouteType routeType) {
		if (edgeIds.length == 0) {
			return Map.of();
		}

		Map<Long, Double> costs = new HashMap<>();

		// Load walk safety costs
		if (routeType.usesWalkSafeCosts()) {
			List<WalkSafeEdgeCostEntity> safeCosts = walkSafeEdgeCostRepository.findByEdgeIds(edgeIds);
			for (WalkSafeEdgeCostEntity cost : safeCosts) {
				costs.put(cost.getEdgeId(), cost.getCostSeconds());
			}
		}

		// Load walk accessibility costs
		if (routeType.usesWalkAccessibilityCosts()) {
			List<WalkAccessibilityEdgeCostEntity> accessibilityCosts =
					walkAccessibilityEdgeCostRepository.findByEdgeIds(edgeIds);
			for (WalkAccessibilityEdgeCostEntity cost : accessibilityCosts) {
				Double existing = costs.get(cost.getEdgeId());
				if (existing != null) {
					// Combine: use the higher cost (more conservative for safety+accessibility)
					costs.put(cost.getEdgeId(), Math.max(existing, cost.getCostSeconds()));
				} else {
					costs.put(cost.getEdgeId(), cost.getCostSeconds());
				}
			}
		}

		// TODO: Add drive hazard costs when drive_edge_costs table is available
		// if (routeType.usesDriveHazardCosts()) { ... }

		return costs;
	}
}
