package com.lpdg.sentinel.common.errors;

/**
 * Thrown when a raw gateway ID cannot be normalized to a valid 12-char uppercase hex string.
 */
public class MalformedGatewayIdException extends SentinelException {

    public MalformedGatewayIdException(String message) {
        super("MALFORMED_GATEWAY_ID", message);
    }
}
