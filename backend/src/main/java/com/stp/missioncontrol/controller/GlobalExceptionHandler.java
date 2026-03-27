package com.stp.missioncontrol.controller;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleConflict(IllegalStateException ex) {
        return buildResponse(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntimeException(RuntimeException ex) {
        String message = ex.getMessage() != null ? ex.getMessage() : "Internal server error";

        // Kafka broker without an Authorizer configured
        if (containsCause(ex, "SecurityDisabledException")
                || message.contains("No Authorizer is configured")) {
            return buildResponse(HttpStatus.SERVICE_UNAVAILABLE,
                    "ACL operations are not available \u2014 the Kafka broker does not have an Authorizer configured.");
        }

        // Log full detail server-side, return sanitized message to client
        log.error("Unhandled runtime exception", ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, sanitize(message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneral(Exception ex) {
        // Log full detail server-side, return generic message to client
        log.error("Unhandled exception", ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred. Check server logs for details.");
    }

    private ResponseEntity<Map<String, Object>> buildResponse(HttpStatus status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        body.put("timestamp", Instant.now().toString());
        return ResponseEntity.status(status).body(body);
    }

    /**
     * Strips infrastructure details (broker addresses, Java class names, stack fragments)
     * from exception messages before returning them to API clients.
     */
    private String sanitize(String message) {
        if (message == null || message.isBlank()) {
            return "Internal server error";
        }
        // Strip host:port patterns (e.g., "broker1.internal:9092")
        String cleaned = message.replaceAll("[\\w.-]+:\\d{2,5}", "***");
        // Strip Java class names (e.g., "org.apache.kafka.common.errors.TimeoutException")
        cleaned = cleaned.replaceAll("[a-z]+\\.[a-z]+\\.[a-z]+[\\w.]*Exception", "error");
        // Strip stack trace fragments (e.g., "at com.foo.Bar.method(Bar.java:42)")
        cleaned = cleaned.replaceAll("at [\\w.$]+\\([^)]*\\)", "");
        // Collapse excessive whitespace
        cleaned = cleaned.replaceAll("\\s{2,}", " ").trim();
        return cleaned.isEmpty() ? "Internal server error" : cleaned;
    }

    private boolean containsCause(Throwable t, String className) {
        Throwable current = t;
        while (current != null) {
            if (current.getClass().getSimpleName().equals(className)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
