package com.team.GroundTruth.routing.exception;

/**
 * Exception thrown when no route can be found in the extracted subgraph.
 */
public class NoRouteFoundException extends RoutingException {

	/**
	 * Creates a no-route-found exception with the given message.
	 *
	 * @param message error message
	 */
	public NoRouteFoundException(String message) {
		super(message);
	}
}
