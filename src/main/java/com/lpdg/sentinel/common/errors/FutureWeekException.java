package com.lpdg.sentinel.common.errors;

/**
 * Thrown when a prediction week falls in the future beyond available telemetry data.
 */
public class FutureWeekException extends SentinelException {

    public FutureWeekException(String message) {
        super("FUTURE_WEEK", message);
    }
}
