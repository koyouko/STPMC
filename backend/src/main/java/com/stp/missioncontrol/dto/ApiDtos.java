package com.stp.missioncontrol.dto;

import com.stp.missioncontrol.model.MissionControlEnums.AuthProfileType;
import com.stp.missioncontrol.model.MissionControlEnums.CheckSource;
import com.stp.missioncontrol.model.MissionControlEnums.ClusterEnvironment;
import com.stp.missioncontrol.model.MissionControlEnums.ComponentKind;
import com.stp.missioncontrol.model.MissionControlEnums.ConnectionMode;
import com.stp.missioncontrol.model.MissionControlEnums.HealthStatus;
import com.stp.missioncontrol.model.MissionControlEnums.RefreshOperationStatus;
import com.stp.missioncontrol.model.MissionControlEnums.ServiceEndpointProtocol;
import com.stp.missioncontrol.model.MissionControlEnums.TokenScope;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ApiDtos {

    private ApiDtos() {
    }

    public record AuthProfileRequest(
            @NotBlank String name,
            @NotNull AuthProfileType type,
            @NotBlank String securityProtocol,
            String truststorePath,
            String truststorePasswordFile,
            String keystorePath,
            String keystorePasswordFile,
            String keyPasswordFile,
            String principal,
            String keytabPath,
            String krb5ConfigPath,
            String saslServiceName
    ) {
    }

    public record ClusterListenerRequest(
            @NotBlank String name,
            @NotBlank String host,
            int port,
            boolean preferred,
            @NotNull @Valid AuthProfileRequest authProfile
    ) {
    }

    public record ServiceEndpointRequest(
            @NotNull ComponentKind kind,
            @NotNull ServiceEndpointProtocol protocol,
            String baseUrl,
            String host,
            Integer port,
            String healthPath,
            String version
    ) {
    }

    public record CreateClusterRequest(
            @NotBlank String name,
            @NotNull ClusterEnvironment environment,
            String description,
            @NotEmpty List<@Valid ClusterListenerRequest> listeners,
            List<@Valid ServiceEndpointRequest> serviceEndpoints
    ) {
    }

    public record ComponentHealthResponse(
            ComponentKind kind,
            HealthStatus status,
            CheckSource checkSource,
            String endpoint,
            Long latencyMs,
            String message,
            String version,
            Instant lastCheckedAt
    ) {
    }

    public record ClusterHealthSummaryResponse(
            UUID clusterId,
            String clusterName,
            ClusterEnvironment environment,
            ConnectionMode connectionMode,
            HealthStatus status,
            String summaryMessage,
            Instant lastCheckedAt,
            Instant staleAfter,
            List<ComponentHealthResponse> components
    ) {
    }

    public record ClusterHealthDetailResponse(
            UUID clusterId,
            String clusterName,
            ClusterEnvironment environment,
            ConnectionMode connectionMode,
            HealthStatus status,
            String summaryMessage,
            Instant lastCheckedAt,
            Instant staleAfter,
            List<ComponentHealthResponse> components
    ) {
    }

    public record CreateServiceAccountRequest(
            @NotBlank String name,
            String description,
            @NotEmpty Set<TokenScope> scopes,
            @NotEmpty Set<ClusterEnvironment> allowedEnvironments,
            Set<UUID> allowedClusterIds
    ) {
    }

    public record ServiceAccountTokenRequest(
            @NotBlank String name,
            Instant expiresAt
    ) {
    }

    public record ServiceAccountTokenResponse(
            UUID tokenId,
            String name,
            String tokenPrefix,
            Instant createdAt,
            Instant expiresAt,
            Instant lastUsedAt,
            boolean revoked,
            String rawToken
    ) {
    }

    public record ServiceAccountResponse(
            UUID id,
            String name,
            String description,
            boolean active,
            Instant createdAt,
            Set<TokenScope> scopes,
            Set<ClusterEnvironment> allowedEnvironments,
            Set<UUID> allowedClusterIds,
            List<ServiceAccountTokenResponse> tokens
    ) {
    }

    public record RefreshOperationResponse(
            UUID operationId,
            RefreshOperationStatus status,
            UUID clusterId,
            String message,
            Instant requestedAt
    ) {
    }

    public record UpdateClusterRequest(
            String name,
            String description,
            ClusterEnvironment environment,
            List<@Valid ClusterListenerRequest> listeners,
            List<@Valid ServiceEndpointRequest> serviceEndpoints
    ) {
    }

    public record AuthProfileResponse(
            UUID id,
            String name,
            AuthProfileType type,
            String securityProtocol,
            String truststorePath,
            String keystorePath,
            String principal,
            String keytabPath,
            String krb5ConfigPath,
            String saslServiceName
    ) {
    }

    public record ClusterListenerResponse(
            UUID id,
            String name,
            String host,
            int port,
            boolean preferred,
            AuthProfileResponse authProfile
    ) {
    }

    public record ServiceEndpointResponse(
            UUID id,
            ComponentKind kind,
            ServiceEndpointProtocol protocol,
            String baseUrl,
            String host,
            Integer port,
            String healthPath,
            String version,
            boolean enabled
    ) {
    }

    public record ClusterConfigResponse(
            UUID clusterId,
            String name,
            String description,
            ClusterEnvironment environment,
            ConnectionMode connectionMode,
            boolean active,
            Instant createdAt,
            Instant updatedAt,
            List<ClusterListenerResponse> listeners,
            List<ServiceEndpointResponse> serviceEndpoints
    ) {
    }

    public record TestConnectionRequest(
            @NotBlank String bootstrapServers,
            @NotNull @Valid AuthProfileRequest authProfile
    ) {
    }

    public record TestConnectionResponse(
            boolean success,
            String clusterId,
            int nodeCount,
            long latencyMs,
            String errorMessage
    ) {
    }

    // ── Audit DTOs ────────────────────────────────────────────────────

    public record AuditEventResponse(
            UUID id,
            String actor,
            String action,
            String entityType,
            String entityId,
            String details,
            Instant createdAt
    ) {
    }

    public record AuditPageResponse(
            List<AuditEventResponse> events,
            long totalElements,
            int totalPages,
            int currentPage
    ) {
    }

    // ── Metrics DTOs ──────────────────────────────────────────────────

    public record MetricsTargetResponse(
            UUID targetId,
            String host,
            int metricsPort,
            String role,
            String label,
            boolean enabled,
            Instant createdAt
    ) {
    }

    /**
     * Metrics scraped from a single target's Prometheus JMX exporter endpoint.
     * Fields are -1.0 when the target was unreachable or the metric was not present.
     * {@code discoveredClusterId} is read from the {@code kafka_server_KafkaServer_ClusterId}
     * JMX metric label — null when the target is unreachable or not a Kafka broker.
     */
    public record BrokerMetricsSample(
            UUID targetId,
            String host,
            int metricsPort,
            String role,
            String label,
            String discoveredClusterId,
            boolean reachable,
            String errorMessage,
            Instant scrapedAt,
            long latencyMs,
            double messagesInPerSec,
            double bytesInPerSec,
            double bytesOutPerSec,
            double underReplicatedPartitions,
            double activeControllerCount,
            double offlinePartitionsCount,
            double brokerState,
            double leaderCount,
            double partitionCount,
            double isrShrinksPerSec,
            double isrExpandsPerSec,
            double requestHandlerIdle,
            double heapUsedBytes,
            double heapMaxBytes
    ) {
    }

    /**
     * A Kafka cluster discovered by grouping brokers that returned the same
     * {@code kafka_server_KafkaServer_ClusterId} label from their JMX scrape.
     * {@code clusterId} is null for unreachable or non-Kafka targets.
     */
    public record DiscoveredCluster(
            String clusterId,
            List<BrokerMetricsSample> brokers
    ) {
    }

    /** Top-level response for an on-demand global scrape. */
    public record MetricsScrapeResponse(
            Instant scrapedAt,
            List<DiscoveredCluster> clusters
    ) {
    }
}
