package com.team.GroundTruth.routing.exception;

/**
 * Base exception for routing failures.
 */
public class RoutingException extends RuntimeException {

	/**
	 * Creates a routing exception with the given message.
	 *
	 * @param message error message
	 */
	public RoutingException(String message) {
		super(message);
	}

	/**
	 * Creates a routing exception with the given message and cause.
	 *
	 * @param message error message
	 * @param cause underlying cause
	 */
	public RoutingException(String message, Throwable cause) {
		super(message, cause);
	}
}
