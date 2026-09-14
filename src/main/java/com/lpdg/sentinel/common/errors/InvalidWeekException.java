package com.lpdg.sentinel.common.errors;

/**
 * Thrown when a week identifier is unparseable or not anchored to a Monday.
 */
public class InvalidWeekException extends SentinelException {

    public InvalidWeekException(String message) {
        super("MALFORMED_WEEK", message);
    }

    public InvalidWeekException(String code, String message) {
        super(code, message);
    }

    public InvalidWeekException(String code, String message, Throwable cause) {
        super(code, message, cause);
    }
}
