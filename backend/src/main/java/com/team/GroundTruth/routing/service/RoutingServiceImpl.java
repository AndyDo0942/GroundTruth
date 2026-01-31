package com.team.GroundTruth.routing.service;

import com.team.GroundTruth.entity.maps.EdgeEntity;
import com.team.GroundTruth.entity.maps.NodeEntity;
import com.team.GroundTruth.routing.astar.AStarRouter;
import com.team.GroundTruth.routing.astar.DirectedEdge;
import com.team.GroundTruth.routing.astar.NodeCoord;
import com.team.GroundTruth.routing.exception.NoRouteFoundException;
import com.team.GroundTruth.routing.exception.NodeSnapException;
import com.team.GroundTruth.routing.exception.RoutingException;
import com.team.GroundTruth.routing.model.Location;
import com.team.GroundTruth.routing.model.RouteResult;
import com.team.GroundTruth.routing.model.TravelMode;
import com.team.GroundTruth.routing.repo.EdgeCostOverlayRepository;
import com.team.GroundTruth.routing.repo.EdgeRepository;
import com.team.GroundTruth.routing.repo.NodeRepository;
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

	private final NodeRepository nodeRepository;
	private final EdgeRepository edgeRepository;
	private final EdgeCostOverlayRepository edgeCostOverlayRepository;
	private final AStarRouter aStarRouter;

	/**
	 * Creates a routing service implementation.
	 *
	 * @param nodeRepository repository used for node queries
	 * @param edgeRepository repository used for edge queries
	 * @param edgeCostOverlayRepository repository used for overlay queries
	 * @param aStarRouter A* router implementation
	 */
	public RoutingServiceImpl(
			NodeRepository nodeRepository,
			EdgeRepository edgeRepository,
			EdgeCostOverlayRepository edgeCostOverlayRepository,
			AStarRouter aStarRouter
	) {
		this.nodeRepository = Objects.requireNonNull(nodeRepository, "nodeRepository");
		this.edgeRepository = Objects.requireNonNull(edgeRepository, "edgeRepository");
		this.edgeCostOverlayRepository = Objects.requireNonNull(edgeCostOverlayRepository, "edgeCostOverlayRepository");
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
		nodeCoords.putIfAbsent(startNodeId, toCoord(startNode));
		nodeCoords.putIfAbsent(endNodeId, toCoord(endNode));

		List<Long> nodeIds = new ArrayList<>(nodeCoords.keySet());
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

		return aStarRouter.route(startNodeId, endNodeId, nodeCoords, outgoingBySource, edgeById);
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

	private Map<Long, OverlayAccumulator> loadOverlays(List<EdgeEntity> edges, TravelMode mode) {
		if (edges.isEmpty()) {
			return Map.of();
		}
		List<Long> edgeIds = edges.stream().map(EdgeEntity::getId).toList();
		Map<Long, OverlayAccumulator> overlayByEdgeId = new HashMap<>();
		OffsetDateTime asOf = OffsetDateTime.now(ZoneOffset.UTC);
		edgeCostOverlayRepository.findActiveOverlays(edgeIds, mode.dbValue(), asOf)
				.forEach(overlay -> overlayByEdgeId
						.computeIfAbsent(overlay.getEdgeId(), key -> new OverlayAccumulator())
						.add(overlay.getCostMultiplier(), overlay.getCostDeltaSeconds()));
		return overlayByEdgeId;
	}

	private static final class OverlayAccumulator {
		private double multiplier = 1.0;
		private double deltaSeconds = 0.0;

		private void add(double multiplier, double deltaSeconds) {
			this.multiplier *= multiplier;
			this.deltaSeconds += deltaSeconds;
		}
	}
}
