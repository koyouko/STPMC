package com.stp.missioncontrol.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.lang.reflect.Method;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTests {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void runtimeExceptionSanitizesBrokerAddresses() {
        RuntimeException ex = new RuntimeException(
                "Timeout connecting to broker1.internal:9092 after 30000ms");

        ResponseEntity<Map<String, Object>> response = handler.handleRuntimeException(ex);

        String message = (String) response.getBody().get("message");
        assertThat(message).doesNotContain("broker1.internal:9092");
        assertThat(message).doesNotContain("9092");
    }

    @Test
    void runtimeExceptionSanitizesJavaClassNames() {
        RuntimeException ex = new RuntimeException(
                "org.apache.kafka.common.errors.TimeoutException: Call timed out");

        ResponseEntity<Map<String, Object>> response = handler.handleRuntimeException(ex);

        String message = (String) response.getBody().get("message");
        assertThat(message).doesNotContain("org.apache.kafka");
        assertThat(message).doesNotContain("TimeoutException");
    }

    @Test
    void runtimeExceptionSanitizesStackTraces() {
        RuntimeException ex = new RuntimeException(
                "Failed at com.stp.missioncontrol.service.HealthService.refresh(HealthService.java:42)");

        ResponseEntity<Map<String, Object>> response = handler.handleRuntimeException(ex);

        String message = (String) response.getBody().get("message");
        assertThat(message).doesNotContain("HealthService.java:42");
        assertThat(message).doesNotContain("com.stp.missioncontrol");
    }

    @Test
    void genericExceptionReturnsOpaqueMessage() {
        Exception ex = new Exception("Database connection pool exhausted for jdbc:postgresql://db.internal:5432/mc");

        ResponseEntity<Map<String, Object>> response = handler.handleGeneral(ex);

        String message = (String) response.getBody().get("message");
        assertThat(message).isEqualTo("An unexpected error occurred. Check server logs for details.");
        assertThat(message).doesNotContain("postgresql");
        assertThat(message).doesNotContain("db.internal");
    }

    @Test
    void illegalArgumentReturnsOriginalMessage() {
        // Validation errors are safe to return — they contain user input, not infra details
        IllegalArgumentException ex = new IllegalArgumentException("Cluster not found: abc-123");

        ResponseEntity<Map<String, Object>> response = handler.handleBadRequest(ex);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody().get("message")).isEqualTo("Cluster not found: abc-123");
    }

    @Test
    void kafkaSecurityDisabledReturns503() {
        // Simulate a SecurityDisabledException wrapped in RuntimeException
        RuntimeException ex = new RuntimeException("No Authorizer is configured on the broker");

        ResponseEntity<Map<String, Object>> response = handler.handleRuntimeException(ex);

        assertThat(response.getStatusCode().value()).isEqualTo(503);
    }
}
