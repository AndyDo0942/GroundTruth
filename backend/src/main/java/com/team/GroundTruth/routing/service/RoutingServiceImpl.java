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

	private static final double EARTH_RADIUS_METERS = 6_371_000.0;
	private static final double DEFAULT_RADIUS_MULTIPLIER = 1.2;
	private static final double MIN_RADIUS_METERS = 2_000.0;

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
}
