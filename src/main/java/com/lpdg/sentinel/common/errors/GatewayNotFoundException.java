package com.lpdg.sentinel.common.errors;

/**
 * Thrown when a gateway is validly formatted but not present in the weekly recommendations.
 */
public class GatewayNotFoundException extends SentinelException {

    public GatewayNotFoundException(String message) {
        super("GATEWAY_NOT_FOUND", message);
    }
}
