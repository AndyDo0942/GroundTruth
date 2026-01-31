package com.team.GroundTruth.exception;

import java.util.UUID;

/**
 * Exception thrown when a hazard report cannot be found.
 */
public class HazardReportNotFoundException extends RuntimeException {
    public static final String ERROR_MESSAGE = "Hazard report with ID '%S' does not exist";

    private final UUID id;

    public HazardReportNotFoundException(UUID id) {
        super(String.format(ERROR_MESSAGE, id));
        this.id = id;
    }

    public UUID getId() {
        return id;
    }
}
