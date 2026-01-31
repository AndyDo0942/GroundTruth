package com.team.GroundTruth.routing.service;

import com.team.GroundTruth.entity.maps.EdgeEntity;
import com.team.GroundTruth.entity.maps.NodeEntity;
import com.team.GroundTruth.routing.astar.AStarRouter;
import com.team.GroundTruth.routing.astar.DirectedEdge;
import com.team.GroundTruth.routing.astar.NodeCoord;
import com.team.GroundTruth.routing.exception.NodeSnapException;
import com.team.GroundTruth.routing.exception.RoutingException;
import com.team.GroundTruth.routing.model.Location;
import com.team.GroundTruth.routing.model.RouteResult;
import com.team.GroundTruth.routing.repo.EdgeRepository;
import com.team.GroundTruth.routing.repo.NodeRepository;
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
	private final AStarRouter aStarRouter;

	/**
	 * Creates a routing service implementation.
	 *
	 * @param nodeRepository repository used for node queries
	 * @param edgeRepository repository used for edge queries
	 * @param aStarRouter A* router implementation
	 */
	public RoutingServiceImpl(
			NodeRepository nodeRepository,
			EdgeRepository edgeRepository,
			AStarRouter aStarRouter
	) {
		this.nodeRepository = Objects.requireNonNull(nodeRepository, "nodeRepository");
		this.edgeRepository = Objects.requireNonNull(edgeRepository, "edgeRepository");
		this.aStarRouter = Objects.requireNonNull(aStarRouter, "aStarRouter");
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public RouteResult route(Location start, Location end, double radiusMeters) {
		Objects.requireNonNull(start, "start");
		Objects.requireNonNull(end, "end");

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
		List<EdgeEntity> subgraphEdges = edgeRepository.loadSubgraphEdges(nodeIds);

		Map<Long, List<DirectedEdge>> outgoingBySource = new HashMap<>();
		Map<Long, DirectedEdge> edgeById = new HashMap<>();
		for (EdgeEntity edge : subgraphEdges) {
			DirectedEdge directed = new DirectedEdge(
					edge.getId(),
					edge.getTarget(),
					edge.getLengthMeters(),
					edge.getCostSeconds()
			);
			outgoingBySource.computeIfAbsent(edge.getSource(), key -> new ArrayList<>()).add(directed);
			edgeById.put(edge.getId(), directed);
		}

		return aStarRouter.route(startNodeId, endNodeId, nodeCoords, outgoingBySource, edgeById);
	}

	private NodeCoord toCoord(NodeEntity node) {
		Point geom = node.getGeom();
		if (geom == null) {
			throw new RoutingException("Node geometry is missing for node " + node.getId() + ".");
		}
		return new NodeCoord(geom.getY(), geom.getX());
	}
}
