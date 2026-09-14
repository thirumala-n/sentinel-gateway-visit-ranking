package com.lpdg.sentinel.common.errors;

/**
 * Thrown when a valid Monday week falls outside the supported historical dataset window.
 */
public class UnsupportedWeekException extends SentinelException {

    public UnsupportedWeekException(String message) {
        super("UNSUPPORTED_WEEK", message);
    }
}
