package com.stp.missioncontrol.service;

import com.stp.missioncontrol.config.AppProperties;
import com.stp.missioncontrol.dto.ApiDtos;
import com.stp.missioncontrol.model.Cluster;
import com.stp.missioncontrol.model.ClusterAuthProfile;
import com.stp.missioncontrol.model.ClusterHealthSnapshot;
import com.stp.missioncontrol.model.ClusterListener;
import com.stp.missioncontrol.model.ComponentHealthSnapshot;
import com.stp.missioncontrol.model.HealthRefreshOperation;
import com.stp.missioncontrol.model.MissionControlEnums.AuthProfileType;
import com.stp.missioncontrol.model.MissionControlEnums.CheckSource;
import com.stp.missioncontrol.model.MissionControlEnums.ComponentKind;
import com.stp.missioncontrol.model.MissionControlEnums.HealthStatus;
import com.stp.missioncontrol.model.MissionControlEnums.RefreshOperationStatus;
import com.stp.missioncontrol.model.MissionControlEnums.RefreshTriggerType;
import com.stp.missioncontrol.model.MissionControlEnums.ServiceEndpointProtocol;
import com.stp.missioncontrol.model.MissionControlEnums.TokenScope;
import com.stp.missioncontrol.model.ServiceEndpoint;
import com.stp.missioncontrol.repository.ClusterHealthSnapshotRepository;
import com.stp.missioncontrol.repository.ClusterRepository;
import com.stp.missioncontrol.repository.HealthRefreshOperationRepository;
import com.stp.missioncontrol.security.ServiceAccountPrincipal;
import jakarta.transaction.Transactional;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.config.SslConfigs;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class HealthService {

    private static final Pattern VERSION_PATTERN = Pattern.compile("\"version\"\\s*:\\s*\"([^\"]+)\"");

    private final ClusterRepository clusterRepository;
    private final ClusterHealthSnapshotRepository clusterHealthSnapshotRepository;
    private final HealthRefreshOperationRepository refreshOperationRepository;
    private final AuditService auditService;
    private final AppProperties properties;
    private final Executor missionControlTaskExecutor;
    private final HttpClient httpClient;

    public HealthService(
            ClusterRepository clusterRepository,
            ClusterHealthSnapshotRepository clusterHealthSnapshotRepository,
            HealthRefreshOperationRepository refreshOperationRepository,
            AuditService auditService,
            AppProperties properties,
            Executor missionControlTaskExecutor
    ) {
        this.clusterRepository = clusterRepository;
        this.clusterHealthSnapshotRepository = clusterHealthSnapshotRepository;
        this.refreshOperationRepository = refreshOperationRepository;
        this.auditService = auditService;
        this.properties = properties;
        this.missionControlTaskExecutor = missionControlTaskExecutor;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.health().probeTimeoutMs()))
                .build();
    }

    public List<ApiDtos.ClusterHealthSummaryResponse> listClusterHealth(Optional<ServiceAccountPrincipal> principal) {
        principal.ifPresent(current -> requireAnyScope(current, TokenScope.HEALTH_READ, TokenScope.CLUSTER_READ));
        return clusterRepository.findAll().stream()
                .filter(cluster -> principal.map(p -> p.canAccessCluster(cluster)).orElse(true))
                .map(this::toSummary)
                .toList();
    }

    public ApiDtos.ClusterHealthDetailResponse getClusterHealth(UUID clusterId, Optional<ServiceAccountPrincipal> principal) {
        principal.ifPresent(current -> requireAnyScope(current, TokenScope.HEALTH_READ, TokenScope.CLUSTER_READ));
        Cluster cluster = clusterRepository.findDetailedById(clusterId)
                .orElseThrow(() -> new IllegalArgumentException("Cluster not found"));
        if (principal.isPresent() && !principal.get().canAccessCluster(cluster)) {
            throw new IllegalArgumentException("Cluster is not in scope for this token");
        }
        return toDetail(cluster);
    }

    @Transactional
    public ApiDtos.RefreshOperationResponse queueRefresh(UUID clusterId, String requestedBy, Optional<ServiceAccountPrincipal> principal) {
        Cluster cluster = clusterRepository.findDetailedById(clusterId)
                .orElseThrow(() -> new IllegalArgumentException("Cluster not found"));
        if (principal.isPresent()) {
            if (!principal.get().hasScope(TokenScope.HEALTH_REFRESH)) {
                throw new IllegalArgumentException("Token is missing health refresh scope");
            }
            if (!principal.get().canAccessCluster(cluster)) {
                throw new IllegalArgumentException("Cluster is not in scope for this token");
            }
        }

        Instant cooldownBoundary = Instant.now().minusMillis(properties.health().refreshCooldownMs());
        Optional<HealthRefreshOperation> recentOperation = refreshOperationRepository
                .findFirstByClusterIdAndRequestedAtAfterOrderByRequestedAtDesc(clusterId, cooldownBoundary);
        if (recentOperation.isPresent()) {
            HealthRefreshOperation operation = recentOperation.get();
            return new ApiDtos.RefreshOperationResponse(
                    operation.getId(),
                    RefreshOperationStatus.RATE_LIMITED,
                    clusterId,
                    "Refresh cooldown is active",
                    operation.getRequestedAt()
            );
        }

        HealthRefreshOperation operation = refreshOperationRepository.save(new HealthRefreshOperation(cluster, RefreshTriggerType.API, requestedBy));
        missionControlTaskExecutor.execute(() -> refreshCluster(clusterId, operation.getId()));
        auditService.record(requestedBy, "HEALTH_REFRESH_QUEUED", "Cluster", clusterId.toString(), cluster.getName());
        return new ApiDtos.RefreshOperationResponse(
                operation.getId(),
                operation.getStatus(),
                clusterId,
                "Refresh queued",
                operation.getRequestedAt()
        );
    }

    @Transactional
    public ApiDtos.RefreshOperationResponse queuePlatformRefresh(UUID clusterId, String requestedBy) {
        return queueRefresh(clusterId, requestedBy, Optional.empty());
    }

    @Transactional
    public void refreshClusterImmediately(UUID clusterId, String requestedBy) {
        Cluster cluster = clusterRepository.findDetailedById(clusterId)
                .orElseThrow(() -> new IllegalArgumentException("Cluster not found"));
        HealthRefreshOperation operation = refreshOperationRepository.save(new HealthRefreshOperation(cluster, RefreshTriggerType.API, requestedBy));
        refreshCluster(clusterId, operation.getId());
    }

    @Scheduled(
            fixedDelayString = "${app.health.poll-interval-ms:60000}",
            initialDelayString = "${app.health.poll-interval-ms:60000}"
    )
    public void refreshAllSnapshots() {
        clusterRepository.findAll().stream()
                .filter(Cluster::isActive)
                .forEach(cluster -> {
                    HealthRefreshOperation operation = refreshOperationRepository.save(
                            new HealthRefreshOperation(cluster, RefreshTriggerType.SCHEDULED, "scheduler")
                    );
                    missionControlTaskExecutor.execute(() -> refreshCluster(cluster.getId(), operation.getId()));
                });
    }

    @Transactional
    public ApiDtos.ClusterHealthSummaryResponse toSummary(Cluster cluster) {
        ClusterHealthSnapshot snapshot = ensureSnapshot(cluster);
        HealthStatus status = effectiveStatus(snapshot);
        return new ApiDtos.ClusterHealthSummaryResponse(
                cluster.getId(),
                cluster.getName(),
                cluster.getEnvironment(),
                cluster.getConnectionMode(),
                status,
                snapshot.getSummaryMessage(),
                snapshot.getLastCheckedAt(),
                snapshot.getStaleAfter(),
                snapshot.getComponents().stream()
                        .map(this::toComponentResponse)
                        .toList()
        );
    }

    @Transactional
    public ApiDtos.ClusterHealthDetailResponse toDetail(Cluster cluster) {
        ClusterHealthSnapshot snapshot = ensureSnapshot(cluster);
        return new ApiDtos.ClusterHealthDetailResponse(
                cluster.getId(),
                cluster.getName(),
                cluster.getEnvironment(),
                cluster.getConnectionMode(),
                effectiveStatus(snapshot),
                snapshot.getSummaryMessage(),
                snapshot.getLastCheckedAt(),
                snapshot.getStaleAfter(),
                snapshot.getComponents().stream()
                        .map(this::toComponentResponse)
                        .toList()
        );
    }

    @Transactional
    protected void refreshCluster(UUID clusterId, UUID operationId) {
        Cluster cluster = clusterRepository.findDetailedById(clusterId)
                .orElseThrow(() -> new IllegalArgumentException("Cluster not found"));
        HealthRefreshOperation operation = refreshOperationRepository.findById(operationId)
                .orElseThrow(() -> new IllegalArgumentException("Refresh operation not found"));
        operation.setStatus(RefreshOperationStatus.RUNNING);
        operation.setStartedAt(Instant.now());
        refreshOperationRepository.save(operation);

        Instant refreshStarted = Instant.now();

        try {
            List<ComponentHealthSnapshot> components = new ArrayList<>();
            components.add(probeKafka(cluster));

            for (ComponentKind kind : List.of(ComponentKind.ZOOKEEPER, ComponentKind.SCHEMA_REGISTRY, ComponentKind.CONTROL_CENTER, ComponentKind.PROMETHEUS)) {
                List<ServiceEndpoint> endpoints = cluster.getServiceEndpoints().stream()
                        .filter(ServiceEndpoint::isEnabled)
                        .filter(endpoint -> endpoint.getKind() == kind)
                        .toList();
                if (endpoints.isEmpty()) {
                    components.add(notApplicable(kind));
                } else {
                    endpoints.forEach(endpoint -> components.add(probeEndpoint(endpoint)));
                }
            }

            ClusterHealthSnapshot snapshot = ensureSnapshot(cluster);
            snapshot.replaceComponents(components);
            snapshot.setLastCheckedAt(Instant.now());
            snapshot.setStaleAfter(snapshot.getLastCheckedAt().plusMillis(properties.health().staleAfterMs()));
            snapshot.setStatus(aggregateStatus(components, snapshot.getStaleAfter()));
            snapshot.setSummaryMessage(buildSummaryMessage(snapshot.getStatus(), components));
            snapshot.setRefreshDurationMs(Duration.between(refreshStarted, Instant.now()).toMillis());
            clusterHealthSnapshotRepository.save(snapshot);

            operation.setStatus(RefreshOperationStatus.SUCCESS);
            operation.setCompletedAt(Instant.now());
            operation.setMessage(snapshot.getSummaryMessage());
            refreshOperationRepository.save(operation);
            auditService.record(operation.getRequestedBy(), "HEALTH_REFRESH_COMPLETED", "Cluster", clusterId.toString(), snapshot.getSummaryMessage());
        } catch (Exception exception) {
            operation.setStatus(RefreshOperationStatus.FAILED);
            operation.setCompletedAt(Instant.now());
            operation.setMessage(exception.getMessage());
            refreshOperationRepository.save(operation);
            auditService.record(operation.getRequestedBy(), "HEALTH_REFRESH_FAILED", "Cluster", clusterId.toString(), exception.getMessage());
        }
    }

    private ClusterHealthSnapshot ensureSnapshot(Cluster cluster) {
        ClusterHealthSnapshot snapshot = cluster.getHealthSnapshot();
        if (snapshot == null) {
            snapshot = new ClusterHealthSnapshot(cluster);
            cluster.setHealthSnapshot(snapshot);
            clusterRepository.save(cluster);
        }
        return snapshot;
    }

    private ComponentHealthSnapshot probeKafka(Cluster cluster) {
        ClusterListener listener = cluster.getListeners().stream()
                .filter(ClusterListener::isPreferred)
                .findFirst()
                .orElseGet(() -> cluster.getListeners().stream().findFirst().orElse(null));

        Instant checkedAt = Instant.now();
        if (listener == null) {
            return new ComponentHealthSnapshot(
                    ComponentKind.KAFKA,
                    HealthStatus.DOWN,
                    CheckSource.KAFKA_CLIENT,
                    "No listener configured",
                    0L,
                    "Kafka listener is missing",
                    null,
                    checkedAt
            );
        }

        Map<String, Object> config = new HashMap<>();
        config.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, listener.getBootstrapServer());
        config.put(AdminClientConfig.CLIENT_ID_CONFIG, "mission-control-health-" + cluster.getId());
        config.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, properties.health().kafkaTimeoutMs());
        config.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, properties.health().kafkaTimeoutMs());

        ClusterAuthProfile authProfile = listener.getAuthProfile();
        config.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, authProfile.getSecurityProtocol());
        applyAuthProfile(config, authProfile);

        Instant started = Instant.now();
        String previousKrb5 = System.getProperty("java.security.krb5.conf");
        try {
            if (authProfile.getType() == AuthProfileType.SASL_GSSAPI && authProfile.getKrb5ConfigPath() != null && !authProfile.getKrb5ConfigPath().isBlank()) {
                System.setProperty("java.security.krb5.conf", authProfile.getKrb5ConfigPath());
            }
            try (AdminClient adminClient = AdminClient.create(config)) {
                String clusterId = adminClient.describeCluster().clusterId().get(properties.health().kafkaTimeoutMs(), TimeUnit.MILLISECONDS);
                int nodeCount = adminClient.describeCluster().nodes().get(properties.health().kafkaTimeoutMs(), TimeUnit.MILLISECONDS).size();
                long latency = Duration.between(started, Instant.now()).toMillis();
                return new ComponentHealthSnapshot(
                        ComponentKind.KAFKA,
                        nodeCount > 0 ? HealthStatus.HEALTHY : HealthStatus.DEGRADED,
                        CheckSource.KAFKA_CLIENT,
                        listener.getBootstrapServer(),
                        latency,
                        "Cluster id " + clusterId + ", brokers " + nodeCount,
                        null,
                        Instant.now()
                );
            }
        } catch (Exception exception) {
            long latency = Duration.between(started, Instant.now()).toMillis();
            return new ComponentHealthSnapshot(
                    ComponentKind.KAFKA,
                    HealthStatus.DOWN,
                    CheckSource.KAFKA_CLIENT,
                    listener.getBootstrapServer(),
                    latency,
                    exception.getMessage(),
                    null,
                    Instant.now()
            );
        } finally {
            if (previousKrb5 == null) {
                System.clearProperty("java.security.krb5.conf");
            } else {
                System.setProperty("java.security.krb5.conf", previousKrb5);
            }
        }
    }

    private void applyAuthProfile(Map<String, Object> config, ClusterAuthProfile authProfile) {
        if (authProfile.getType() == AuthProfileType.PLAINTEXT) {
            return;
        }
        if (authProfile.getTruststorePath() != null && !authProfile.getTruststorePath().isBlank()) {
            config.put(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, authProfile.getTruststorePath());
        }
        readSecret(authProfile.getTruststorePasswordFile()).ifPresent(secret ->
                config.put(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG, secret));

        if (authProfile.getType() == AuthProfileType.MTLS_SSL) {
            if (authProfile.getKeystorePath() != null && !authProfile.getKeystorePath().isBlank()) {
                config.put(SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG, authProfile.getKeystorePath());
            }
            readSecret(authProfile.getKeystorePasswordFile()).ifPresent(secret ->
                    config.put(SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG, secret));
            readSecret(authProfile.getKeyPasswordFile()).ifPresent(secret ->
                    config.put(SslConfigs.SSL_KEY_PASSWORD_CONFIG, secret));
        }

        if (authProfile.getType() == AuthProfileType.SASL_GSSAPI) {
            config.put(SaslConfigs.SASL_MECHANISM, "GSSAPI");
            config.put(SaslConfigs.SASL_KERBEROS_SERVICE_NAME, authProfile.getSaslServiceName() == null ? "kafka" : authProfile.getSaslServiceName());
            String jaasConfig = String.format(
                    "com.sun.security.auth.module.Krb5LoginModule required useKeyTab=true storeKey=true keyTab=\"%s\" principal=\"%s\";",
                    authProfile.getKeytabPath(),
                    authProfile.getPrincipal()
            );
            config.put(SaslConfigs.SASL_JAAS_CONFIG, jaasConfig);
        }
    }

    private ComponentHealthSnapshot probeEndpoint(ServiceEndpoint endpoint) {
        return switch (endpoint.getProtocol()) {
            case HTTP, HTTPS -> probeHttpEndpoint(endpoint);
            case TCP -> probeTcpEndpoint(endpoint);
        };
    }

    private ComponentHealthSnapshot probeHttpEndpoint(ServiceEndpoint endpoint) {
        Instant started = Instant.now();
        String url = endpoint.getBaseUrl();
        if (endpoint.getHealthPath() != null && !endpoint.getHealthPath().isBlank() && url != null && !url.endsWith(endpoint.getHealthPath())) {
            url = url.endsWith("/") || endpoint.getHealthPath().startsWith("/") ? url + endpoint.getHealthPath() : url + "/" + endpoint.getHealthPath();
        }

        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .GET()
                    .timeout(Duration.ofMillis(properties.health().probeTimeoutMs()))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            long latency = Duration.between(started, Instant.now()).toMillis();
            HealthStatus status = response.statusCode() < 400 ? HealthStatus.HEALTHY : HealthStatus.DEGRADED;
            return new ComponentHealthSnapshot(
                    endpoint.getKind(),
                    status,
                    endpoint.getKind() == ComponentKind.PROMETHEUS ? CheckSource.PROMETHEUS : CheckSource.HTTP,
                    endpoint.describeEndpoint(),
                    latency,
                    "HTTP " + response.statusCode(),
                    extractVersion(response.body(), endpoint.getVersion()),
                    Instant.now()
            );
        } catch (Exception exception) {
            return new ComponentHealthSnapshot(
                    endpoint.getKind(),
                    HealthStatus.DOWN,
                    endpoint.getKind() == ComponentKind.PROMETHEUS ? CheckSource.PROMETHEUS : CheckSource.HTTP,
                    endpoint.describeEndpoint(),
                    Duration.between(started, Instant.now()).toMillis(),
                    exception.getMessage(),
                    endpoint.getVersion(),
                    Instant.now()
            );
        }
    }

    private ComponentHealthSnapshot probeTcpEndpoint(ServiceEndpoint endpoint) {
        Instant started = Instant.now();
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(endpoint.getHost(), endpoint.getPort()), properties.health().probeTimeoutMs());
            return new ComponentHealthSnapshot(
                    endpoint.getKind(),
                    HealthStatus.HEALTHY,
                    CheckSource.TCP,
                    endpoint.describeEndpoint(),
                    Duration.between(started, Instant.now()).toMillis(),
                    "TCP probe succeeded",
                    endpoint.getVersion(),
                    Instant.now()
            );
        } catch (IOException exception) {
            return new ComponentHealthSnapshot(
                    endpoint.getKind(),
                    HealthStatus.DOWN,
                    CheckSource.TCP,
                    endpoint.describeEndpoint(),
                    Duration.between(started, Instant.now()).toMillis(),
                    exception.getMessage(),
                    endpoint.getVersion(),
                    Instant.now()
            );
        }
    }

    private ComponentHealthSnapshot notApplicable(ComponentKind kind) {
        return new ComponentHealthSnapshot(
                kind,
                HealthStatus.NOT_APPLICABLE,
                CheckSource.HTTP,
                null,
                0L,
                "Component is not configured",
                null,
                Instant.now()
        );
    }

    private HealthStatus aggregateStatus(List<ComponentHealthSnapshot> components, Instant staleAfter) {
        ComponentHealthSnapshot kafka = components.stream()
                .filter(component -> component.getKind() == ComponentKind.KAFKA)
                .findFirst()
                .orElse(null);
        if (kafka == null) {
            return HealthStatus.UNKNOWN;
        }
        if (kafka.getStatus() == HealthStatus.DOWN) {
            return HealthStatus.DOWN;
        }
        if (kafka.getStatus() == HealthStatus.UNKNOWN) {
            return HealthStatus.UNKNOWN;
        }
        boolean optionalFailure = components.stream()
                .filter(component -> component.getKind() != ComponentKind.KAFKA)
                .anyMatch(component -> component.getStatus() == HealthStatus.DOWN || component.getStatus() == HealthStatus.DEGRADED);
        boolean stale = staleAfter != null && Instant.now().isAfter(staleAfter);
        if (optionalFailure || stale || kafka.getStatus() == HealthStatus.DEGRADED) {
            return HealthStatus.DEGRADED;
        }
        return HealthStatus.HEALTHY;
    }

    private String buildSummaryMessage(HealthStatus status, List<ComponentHealthSnapshot> components) {
        long healthyCount = components.stream().filter(component -> component.getStatus() == HealthStatus.HEALTHY).count();
        long degradedCount = components.stream().filter(component -> component.getStatus() == HealthStatus.DEGRADED).count();
        long downCount = components.stream().filter(component -> component.getStatus() == HealthStatus.DOWN).count();
        return switch (status) {
            case HEALTHY -> "Kafka core is healthy across " + healthyCount + " components";
            case DEGRADED -> "Cluster is degraded: " + degradedCount + " degraded, " + downCount + " down";
            case DOWN -> "Kafka is unavailable";
            case UNKNOWN -> "Cluster health has not been established";
            case NOT_APPLICABLE -> "Not applicable";
        };
    }

    private ApiDtos.ComponentHealthResponse toComponentResponse(ComponentHealthSnapshot component) {
        return new ApiDtos.ComponentHealthResponse(
                component.getKind(),
                component.getStatus(),
                component.getCheckSource(),
                component.getEndpoint(),
                component.getLatencyMs(),
                component.getMessage(),
                component.getVersion(),
                component.getLastCheckedAt()
        );
    }

    private HealthStatus effectiveStatus(ClusterHealthSnapshot snapshot) {
        if (snapshot.getLastCheckedAt() == null) {
            return HealthStatus.UNKNOWN;
        }
        if (snapshot.getStaleAfter() != null && Instant.now().isAfter(snapshot.getStaleAfter()) && snapshot.getStatus() == HealthStatus.HEALTHY) {
            return HealthStatus.DEGRADED;
        }
        return snapshot.getStatus();
    }

    private void requireAnyScope(ServiceAccountPrincipal principal, TokenScope... scopes) {
        for (TokenScope scope : scopes) {
            if (principal.hasScope(scope)) {
                return;
            }
        }
        throw new IllegalArgumentException("Token is missing required scopes");
    }

    private Optional<String> readSecret(String path) {
        if (path == null || path.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.readString(Path.of(path)).trim());
        } catch (IOException exception) {
            return Optional.empty();
        }
    }

    private String extractVersion(String body, String fallbackVersion) {
        Matcher matcher = VERSION_PATTERN.matcher(body);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return fallbackVersion;
    }
}
