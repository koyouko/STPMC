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
}
