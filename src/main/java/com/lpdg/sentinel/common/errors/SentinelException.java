package com.lpdg.sentinel.common.errors;

/**
 * Base runtime exception for all Sentinel domain and application errors.
 */
public class SentinelException extends RuntimeException {

    private final String errorCode;

    public SentinelException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public SentinelException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
