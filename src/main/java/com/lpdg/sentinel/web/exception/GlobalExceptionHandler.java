package com.lpdg.sentinel.web.exception;

import com.lpdg.sentinel.common.errors.FutureWeekException;
import com.lpdg.sentinel.common.errors.GatewayNotFoundException;
import com.lpdg.sentinel.common.errors.InvalidComparisonException;
import com.lpdg.sentinel.common.errors.InvalidWeekException;
import com.lpdg.sentinel.common.errors.MalformedGatewayIdException;
import com.lpdg.sentinel.common.errors.SentinelException;
import com.lpdg.sentinel.common.errors.UnsupportedWeekException;
import com.lpdg.sentinel.web.dto.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Global REST exception handler producing consistent, operator-friendly {@link ApiErrorResponse} objects.
 *
 * <p>Sanitizes error output:
 * <ul>
 *   <li>Never leaks stack traces, source file paths, database schemas, or internal secrets.</li>
 *   <li>Maps validation and domain errors to unambiguous HTTP status codes.</li>
 *   <li>Maintains actionable error codes for automated client evaluation.</li>
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(InvalidWeekException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidWeek(
            InvalidWeekException ex, HttpServletRequest request) {
        log.warn("Invalid week input on {}: {}", request.getRequestURI(), ex.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getErrorCode(), ex.getMessage(), request);
    }

    @ExceptionHandler(MalformedGatewayIdException.class)
    public ResponseEntity<ApiErrorResponse> handleMalformedGatewayId(
            MalformedGatewayIdException ex, HttpServletRequest request) {
        log.warn("Malformed gateway ID on {}: {}", request.getRequestURI(), ex.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getErrorCode(), ex.getMessage(), request);
    }

    @ExceptionHandler(InvalidComparisonException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidComparison(
            InvalidComparisonException ex, HttpServletRequest request) {
        log.warn("Invalid comparison on {}: {}", request.getRequestURI(), ex.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getErrorCode(), ex.getMessage(), request);
    }

    @ExceptionHandler(GatewayNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleGatewayNotFound(
            GatewayNotFoundException ex, HttpServletRequest request) {
        log.info("Gateway not found on {}: {}", request.getRequestURI(), ex.getMessage());
        return buildResponse(HttpStatus.NOT_FOUND, ex.getErrorCode(), ex.getMessage(), request);
    }

    @ExceptionHandler(UnsupportedWeekException.class)
    public ResponseEntity<ApiErrorResponse> handleUnsupportedWeek(
            UnsupportedWeekException ex, HttpServletRequest request) {
        log.warn("Unsupported week on {}: {}", request.getRequestURI(), ex.getMessage());
        return buildResponse(HttpStatus.UNPROCESSABLE_ENTITY, ex.getErrorCode(), ex.getMessage(), request);
    }

    @ExceptionHandler(FutureWeekException.class)
    public ResponseEntity<ApiErrorResponse> handleFutureWeek(
            FutureWeekException ex, HttpServletRequest request) {
        log.warn("Future week on {}: {}", request.getRequestURI(), ex.getMessage());
        return buildResponse(HttpStatus.UNPROCESSABLE_ENTITY, ex.getErrorCode(), ex.getMessage(), request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(
            IllegalArgumentException ex, HttpServletRequest request) {
        log.warn("Illegal argument on {}: {}", request.getRequestURI(), ex.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, "BAD_REQUEST", ex.getMessage(), request);
    }

    @ExceptionHandler({
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class,
            HttpMessageNotReadableException.class
    })
    public ResponseEntity<ApiErrorResponse> handleBindingErrors(
            Exception ex, HttpServletRequest request) {
        log.warn("Bad request parameter on {}: {}", request.getRequestURI(), ex.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, "INVALID_REQUEST_PARAMETERS", ex.getMessage(), request);
    }

    @ExceptionHandler(SentinelException.class)
    public ResponseEntity<ApiErrorResponse> handleSentinelException(
            SentinelException ex, HttpServletRequest request) {
        log.error("Sentinel application error on {}: {}", request.getRequestURI(), ex.getMessage());
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, ex.getErrorCode(), ex.getMessage(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(
            Exception ex, HttpServletRequest request) {
        log.error("Unhandled internal error on {}", request.getRequestURI(), ex);
        return buildResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR",
                "An unexpected internal error occurred. Please contact system operations.",
                request);
    }

    private ResponseEntity<ApiErrorResponse> buildResponse(
            HttpStatus status, String code, String message, HttpServletRequest request) {
        ApiErrorResponse body = new ApiErrorResponse(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                code,
                message,
                request.getRequestURI());
        return ResponseEntity.status(status).body(body);
    }
}
