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
import com.stp.missioncontrol.model.MissionControlEnums.SelfServiceCategory;
import com.stp.missioncontrol.model.MissionControlEnums.SelfServiceTaskType;
import jakarta.validation.constraints.Min;
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

    // ── Self-Service Request DTOs ─────────────────────────────────────

    public record TopicOperationRequest(
            @NotBlank String topicName
    ) {
    }

    public record CreateTopicRequest(
            @NotBlank String topicName,
            @Min(1) int numPartitions,
            @Min(1) short replicationFactor,
            Map<String, String> configs
    ) {
    }

    public record IncreasePartitionsRequest(
            @NotBlank String topicName,
            @Min(1) int newPartitionCount
    ) {
    }

    public record TopicConfigAlterRequest(
            @NotBlank String topicName,
            Map<String, String> configsToSet,
            List<String> configsToDelete
    ) {
    }

    public record AclDescribeRequest(
            String principal,
            String resourceName,
            String resourceType
    ) {
    }

    public record AclGrantRequest(
            @NotBlank String principal,
            @NotBlank String resourceName,
            @NotBlank String resourceType,
            @NotBlank String patternType,
            @NotBlank String operation,
            String permission
    ) {
    }

    public record AclRemoveRequest(
            @NotBlank String principal,
            String resourceName,
            String resourceType,
            String patternType,
            String operation,
            String permission
    ) {
    }

    public record ConsumerGroupRequest(
            @NotBlank String groupId
    ) {
    }

    public record OffsetResetRequest(
            @NotBlank String groupId,
            @NotBlank String resetType,
            Map<Integer, Long> partitionOffsets
    ) {
    }

    public record TopicDataDumpRequest(
            @NotBlank String topicName,
            @Min(1) int maxMessages,
            Integer partition
    ) {
    }

    // ── Self-Service Response DTOs ────────────────────────────────────

    public record TaskCatalogEntry(
            SelfServiceTaskType taskType,
            SelfServiceCategory category,
            String displayName,
            String description,
            boolean readOnly
    ) {
    }

    public record TopicSummary(
            String name,
            boolean internal,
            int partitions
    ) {
    }

    public record TopicListResponse(
            UUID clusterId,
            List<TopicSummary> topics
    ) {
    }

    public record TopicPartitionInfo(
            int partition,
            int leader,
            List<Integer> replicas,
            List<Integer> isr
    ) {
    }

    public record TopicDescribeResponse(
            UUID clusterId,
            String topicName,
            int partitions,
            int replicationFactor,
            List<TopicPartitionInfo> partitionInfos,
            Map<String, String> configs
    ) {
    }

    public record CreateTopicResponse(
            UUID clusterId,
            String topicName,
            String message
    ) {
    }

    public record DeleteTopicResponse(
            UUID clusterId,
            String topicName,
            String message
    ) {
    }

    public record TopicPurgeResponse(
            UUID clusterId,
            String topicName,
            String message
    ) {
    }

    public record IncreasePartitionsResponse(
            UUID clusterId,
            String topicName,
            int previousCount,
            int newCount
    ) {
    }

    public record MessageCountResponse(
            UUID clusterId,
            String topicName,
            Map<Integer, Long> partitionCounts,
            long totalCount
    ) {
    }

    public record TopicConfigEntry(
            String name,
            String value,
            String source,
            boolean isDefault,
            boolean isSensitive,
            boolean isReadOnly
    ) {
    }

    public record TopicConfigDescribeResponse(
            UUID clusterId,
            String topicName,
            List<TopicConfigEntry> configs
    ) {
    }

    public record TopicConfigAlterResponse(
            UUID clusterId,
            String topicName,
            String message,
            Map<String, String> updatedConfigs
    ) {
    }

    public record AclEntry(
            String resourceType,
            String resourceName,
            String patternType,
            String principal,
            String host,
            String operation,
            String permission
    ) {
    }

    public record AclListResponse(
            UUID clusterId,
            List<AclEntry> acls
    ) {
    }

    public record AclOperationResponse(
            UUID clusterId,
            String message,
            int affectedCount
    ) {
    }

    public record ConsumerGroupSummary(
            String groupId,
            String state,
            String type
    ) {
    }

    public record ConsumerGroupListResponse(
            UUID clusterId,
            List<ConsumerGroupSummary> groups
    ) {
    }

    public record ConsumerGroupMemberInfo(
            String memberId,
            String clientId,
            String host,
            List<TopicPartitionAssignment> assignments
    ) {
    }

    public record TopicPartitionAssignment(
            String topic,
            int partition
    ) {
    }

    public record ConsumerGroupOffsetInfo(
            String topic,
            int partition,
            long currentOffset,
            long logEndOffset,
            long lag
    ) {
    }

    public record ConsumerGroupDescribeResponse(
            UUID clusterId,
            String groupId,
            String state,
            String coordinator,
            List<ConsumerGroupMemberInfo> members,
            List<ConsumerGroupOffsetInfo> offsets
    ) {
    }

    public record ConsumerGroupDeleteResponse(
            UUID clusterId,
            String groupId,
            String message
    ) {
    }

    public record OffsetResetResponse(
            UUID clusterId,
            String groupId,
            Map<String, Map<Integer, Long>> updatedOffsets
    ) {
    }

    public record DumpedMessage(
            int partition,
            long offset,
            String key,
            String value,
            long timestamp,
            Map<String, String> headers
    ) {
    }

    public record TopicDataDumpResponse(
            UUID clusterId,
            String topicName,
            List<DumpedMessage> messages
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

    // ── Schema Registry ─────────────────────────────────────────────

    public record SchemaSubjectListResponse(
            UUID clusterId,
            List<String> subjects
    ) {
    }

    public record SchemaSubjectVersionsResponse(
            UUID clusterId,
            String subject,
            List<Integer> versions
    ) {
    }

    public record SchemaResponse(
            UUID clusterId,
            String subject,
            int version,
            int id,
            String schemaType,
            String schema
    ) {
    }
}
