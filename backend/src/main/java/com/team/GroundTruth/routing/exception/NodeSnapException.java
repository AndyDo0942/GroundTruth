package com.team.GroundTruth.routing.exception;

/**
 * Exception thrown when a coordinate cannot be snapped to a graph node.
 */
public class NodeSnapException extends RoutingException {

	/**
	 * Creates a node snap exception with the given message.
	 *
	 * @param message error message
	 */
	public NodeSnapException(String message) {
		super(message);
	}
}
