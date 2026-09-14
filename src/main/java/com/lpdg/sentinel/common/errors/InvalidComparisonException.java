package com.lpdg.sentinel.common.errors;

/**
 * Thrown when an invalid pairwise gateway comparison is requested.
 */
public class InvalidComparisonException extends SentinelException {

    public InvalidComparisonException(String message) {
        super("INVALID_COMPARISON", message);
    }
}
