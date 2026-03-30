package com.stp.missioncontrol.service;

import com.stp.missioncontrol.config.AppProperties;
import com.stp.missioncontrol.dto.ApiDtos;
import com.stp.missioncontrol.model.Cluster;
import com.stp.missioncontrol.model.MetricsTarget;
import com.stp.missioncontrol.model.MissionControlEnums.AuthProfileType;
import com.stp.missioncontrol.model.MissionControlEnums.ClusterEnvironment;
import com.stp.missioncontrol.repository.MetricsTargetRepository;
import com.stp.missioncontrol.service.ClusterService;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class MetricsScraperService {

    private static final Logger log = LoggerFactory.getLogger(MetricsScraperService.class);
    private static final int SCRAPE_TIMEOUT_MS = 5000;
    private static final int MAX_CSV_SIZE_BYTES = 1_048_576; // 1 MB
    private static final int MAX_TARGETS = 500;
    private static final int SCRAPE_PARALLELISM = 10;
    private static final int SCRAPE_OVERALL_TIMEOUT_SECONDS = 120;
    private static final Pattern LABEL_PATTERN = Pattern.compile("(\\w+)=\"([^\"]*)\"");

    // JMX metric that carries the Kafka cluster UUID as a label value
    private static final String CLUSTER_ID_METRIC = "kafka_server_KafkaServer_ClusterId";

    private final MetricsTargetRepository metricsTargetRepository;
    private final ClusterService clusterService;
    private final HttpClient httpClient;
    private final boolean allowLoopback;
    private final ExecutorService metricsScraperExecutor;

    public MetricsScraperService(MetricsTargetRepository metricsTargetRepository,
                                 ClusterService clusterService,
                                 AppProperties properties,
                                 ExecutorService metricsScraperExecutor) {
        this.metricsTargetRepository = metricsTargetRepository;
        this.clusterService = clusterService;
        this.allowLoopback = !"saml".equalsIgnoreCase(properties.security().mode());
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(SCRAPE_TIMEOUT_MS))
                .build();
        this.metricsScraperExecutor = metricsScraperExecutor;
    }

    // ── Inventory management ──────────────────────────────────────────

    /**
     * Replaces the entire global target inventory with targets parsed from the CSV file.
     * Format per line: host, port (optional, default 9404), role (optional), label (optional)
     */
    @Transactional
    public List<MetricsTarget> uploadInventory(MultipartFile file) throws IOException {
        if (file.getSize() > MAX_CSV_SIZE_BYTES) {
            throw new IllegalArgumentException("Inventory file must be under 1 MB");
        }
        String content = new String(file.getBytes(), StandardCharsets.UTF_8);
        List<MetricsTarget> targets = parseInventoryCsv(content);

        if (targets.isEmpty()) {
            throw new IllegalArgumentException("No valid targets found in the uploaded file");
        }
        if (targets.size() > MAX_TARGETS) {
            throw new IllegalArgumentException("Too many targets (" + targets.size() + "). Maximum is " + MAX_TARGETS);
        }

        metricsTargetRepository.deleteAll();
        return metricsTargetRepository.saveAll(targets);
    }

    /**
     * Parses a CSV/text inventory file.
     * <pre>
     * # comment lines are ignored
     * # clusterName, host, port (optional), role (optional), environment (optional)
     * Prod East,broker1.internal,4000,BROKER,PROD
     * Prod East,broker2.internal,4000,BROKER,PROD
     * Dev,localhost,4000
     * </pre>
     */
    private List<MetricsTarget> parseInventoryCsv(String content) {
        List<MetricsTarget> targets = new ArrayList<>();
        int lineNum = 0;
        for (String raw : content.split("\n")) {
            lineNum++;
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;

            String[] parts = line.split(",", -1);

            // Field order: clusterName, host, port, role, environment
            String clusterName = parts[0].trim();

            String host = (parts.length > 1 && !parts[1].trim().isEmpty())
                    ? parts[1].trim()
                    : null;
            if (host == null || host.isEmpty()) {
                log.warn("Line {}: no host specified, skipping", lineNum);
                continue;
            }

            // Validate host is not a restricted address (SSRF prevention)
            if (!isAllowedHost(host)) {
                log.warn("Line {}: host '{}' resolves to a restricted address, skipping", lineNum, host);
                continue;
            }

            int port = 9404;
            if (parts.length > 2 && !parts[2].trim().isEmpty()) {
                try {
                    port = Integer.parseInt(parts[2].trim());
                } catch (NumberFormatException e) {
                    log.warn("Line {}: invalid port '{}', using 9404", lineNum, parts[2].trim());
                }
            }
            if (port < 1 || port > 65535) {
                log.warn("Line {}: port {} out of valid range, using 9404", lineNum, port);
                port = 9404;
            }

            String role = (parts.length > 3 && !parts[3].trim().isEmpty())
                    ? parts[3].trim().toUpperCase()
                    : "BROKER";

            String environment = "NON_PROD";
            if (parts.length > 4 && !parts[4].trim().isEmpty()) {
                String envStr = parts[4].trim().toUpperCase();
                if ("PROD".equals(envStr) || "NON_PROD".equals(envStr)) {
                    environment = envStr;
                } else {
                    log.warn("Line {}: invalid environment '{}', using NON_PROD", lineNum, parts[4].trim());
                }
            }

            MetricsTarget target = new MetricsTarget();
            target.setClusterName(clusterName.isEmpty() ? null : clusterName);
            target.setHost(host);
            target.setMetricsPort(port);
            target.setRole(role);
            target.setEnvironment(environment);
            targets.add(target);
        }
        return targets;
    }

    // ── Scraping ──────────────────────────────────────────────────────

    /**
     * Scrapes all enabled targets, extracts the cluster ID from each broker's
     * {@code kafka_server_KafkaServer_ClusterId} JMX metric label, and groups
     * the results by discovered cluster ID.
     * Unreachable or non-Kafka targets are grouped under a {@code null} cluster ID.
     */
    public ApiDtos.MetricsScrapeResponse scrapeAll() {
        List<MetricsTarget> targets = metricsTargetRepository.findByEnabledTrue();
        if (targets.size() > MAX_TARGETS) {
            targets = targets.subList(0, MAX_TARGETS);
            log.warn("Capping scrape to {} targets", MAX_TARGETS);
        }
        Instant scrapedAt = Instant.now();

        // Scrape in parallel using shared thread pool with overall timeout
        List<CompletableFuture<ApiDtos.BrokerMetricsSample>> futures = targets.stream()
                .map(target -> CompletableFuture.supplyAsync(() -> scrapeTarget(target), metricsScraperExecutor))
                .toList();
        List<ApiDtos.BrokerMetricsSample> samples;
        try {
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                    .get(SCRAPE_OVERALL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            samples = futures.stream().map(CompletableFuture::join).toList();
        } catch (Exception e) {
            log.warn("Scrape timed out or was interrupted, collecting partial results: {}", e.getMessage());
            samples = futures.stream()
                    .filter(f -> f.isDone() && !f.isCompletedExceptionally())
                    .map(f -> f.getNow(null))
                    .filter(s -> s != null)
                    .toList();
        }

        // Group by discoveredClusterId (null key = unreachable / unknown)
        Map<String, List<ApiDtos.BrokerMetricsSample>> grouped = samples.stream()
                .collect(Collectors.groupingBy(
                        s -> s.discoveredClusterId() != null ? s.discoveredClusterId() : "",
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        List<ApiDtos.DiscoveredCluster> clusters = grouped.entrySet().stream()
                .map(e -> new ApiDtos.DiscoveredCluster(
                        e.getKey().isEmpty() ? null : e.getKey(),
                        e.getValue()
                ))
                .toList();

        // Write back discoveredClusterId to each target and persist
        writeBackDiscoveredClusterIds(targets, samples);

        // Auto-onboard: for each discovered cluster ID, create a Cluster entry
        // if one doesn't already exist, using the first reachable broker as bootstrap
        autoOnboardDiscoveredClusters(clusters, targets);

        return new ApiDtos.MetricsScrapeResponse(scrapedAt, clusters);
    }

    private void writeBackDiscoveredClusterIds(List<MetricsTarget> targets,
                                                List<ApiDtos.BrokerMetricsSample> samples) {
        Map<UUID, String> targetToClusterId = new LinkedHashMap<>();
        for (ApiDtos.BrokerMetricsSample sample : samples) {
            if (sample.discoveredClusterId() != null && sample.targetId() != null) {
                targetToClusterId.put(sample.targetId(), sample.discoveredClusterId());
            }
        }
        if (targetToClusterId.isEmpty()) return;

        int changedCount = 0;
        for (MetricsTarget target : targets) {
            String clusterId = targetToClusterId.get(target.getId());
            if (clusterId != null && !clusterId.equals(target.getDiscoveredClusterId())) {
                target.setDiscoveredClusterId(clusterId);
                changedCount++;
            }
        }
        if (changedCount > 0) {
            metricsTargetRepository.saveAll(targets);
            log.info("Updated discoveredClusterId on {} of {} targets", changedCount, targetToClusterId.size());
        }
    }

    private ApiDtos.BrokerMetricsSample scrapeTarget(MetricsTarget target) {
        // Re-validate host at scrape time to prevent DNS rebinding attacks
        // (host may have resolved to a safe IP at CSV upload but changed since)
        if (!isAllowedHost(target.getHost())) {
            return errorSample(target, 0, "Host resolves to a restricted address");
        }
        String url = "http://" + target.getHost() + ":" + target.getMetricsPort() + "/metrics";
        Instant start = Instant.now();
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .GET()
                    .timeout(Duration.ofMillis(SCRAPE_TIMEOUT_MS))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            long latencyMs = Duration.between(start, Instant.now()).toMillis();

            if (response.statusCode() >= 400) {
                return errorSample(target, latencyMs, "HTTP " + response.statusCode());
            }

            List<MetricSample> parsed = parsePrometheusText(response.body());
            return extractBrokerMetrics(target, latencyMs, parsed);

        } catch (Exception e) {
            long latencyMs = Duration.between(start, Instant.now()).toMillis();
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            log.debug("Scrape failed for {}:{} — {}", target.getHost(), target.getMetricsPort(), msg);
            return errorSample(target, latencyMs, msg);
        }
    }

    // ── Prometheus text format parser ─────────────────────────────────

    private record MetricSample(String name, Map<String, String> labels, double value) {
    }

    /**
     * Parses Prometheus text exposition format into a flat list of samples.
     * Lines starting with '#' are skipped. Label values with special characters are handled.
     */
    private List<MetricSample> parsePrometheusText(String text) {
        List<MetricSample> samples = new ArrayList<>();
        for (String raw : text.split("\n")) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;

            String metricName;
            Map<String, String> labels = Map.of();
            String remainder;

            int braceOpen = line.indexOf('{');
            if (braceOpen >= 0) {
                metricName = line.substring(0, braceOpen).trim();
                int braceClose = line.lastIndexOf('}');
                if (braceClose < braceOpen) continue;
                labels = parseLabels(line.substring(braceOpen + 1, braceClose));
                remainder = line.substring(braceClose + 1).trim();
            } else {
                int space = line.indexOf(' ');
                if (space < 0) continue;
                metricName = line.substring(0, space).trim();
                remainder = line.substring(space + 1).trim();
            }

            if (metricName.isEmpty() || remainder.isEmpty()) continue;
            String valueStr = remainder.split("\\s+")[0];
            try {
                double value = Double.parseDouble(valueStr);
                if (!Double.isNaN(value) && !Double.isInfinite(value)) {
                    samples.add(new MetricSample(metricName, labels, value));
                }
            } catch (NumberFormatException ignored) {
                // skip unparseable lines
            }
        }
        return samples;
    }

    private Map<String, String> parseLabels(String labelStr) {
        if (labelStr == null || labelStr.isBlank()) return Map.of();
        Map<String, String> labels = new LinkedHashMap<>();
        Matcher matcher = LABEL_PATTERN.matcher(labelStr);
        while (matcher.find()) {
            labels.put(matcher.group(1), matcher.group(2));
        }
        return labels;
    }

    /**
     * Extracts the Kafka cluster UUID from the {@code kafka_server_KafkaServer_ClusterId}
     * metric. The JMX exporter exports this as a gauge with value 1.0 and the UUID
     * in the {@code clusterId} label, e.g.:
     * <pre>kafka_server_KafkaServer_ClusterId{clusterId="abc-123"} 1.0</pre>
     */
    private String extractClusterId(List<MetricSample> samples) {
        return samples.stream()
                .filter(s -> s.name().equals(CLUSTER_ID_METRIC) && s.labels().containsKey("clusterId"))
                .findFirst()
                .map(s -> s.labels().get("clusterId"))
                .orElse(null);
    }

    /**
     * Returns the value for a metric without a topic label (all-topics aggregate).
     * Falls back to the first matching sample if no unlabeled version exists.
     */
    private double getAggregateMetric(List<MetricSample> samples, String name) {
        return samples.stream()
                .filter(s -> s.name().equals(name) && !s.labels().containsKey("topic"))
                .findFirst()
                .map(MetricSample::value)
                .orElseGet(() -> samples.stream()
                        .filter(s -> s.name().equals(name))
                        .findFirst()
                        .map(MetricSample::value)
                        .orElse(-1.0));
    }

    /** Returns the value for a metric filtered by a specific label match. */
    private double getLabeledMetric(List<MetricSample> samples, String name, String labelKey, String labelValue) {
        return samples.stream()
                .filter(s -> s.name().equals(name) && labelValue.equals(s.labels().get(labelKey)))
                .findFirst()
                .map(MetricSample::value)
                .orElse(-1.0);
    }

    // ── Metric extraction ─────────────────────────────────────────────

    private ApiDtos.BrokerMetricsSample extractBrokerMetrics(
            MetricsTarget target, long latencyMs, List<MetricSample> samples) {

        String discoveredClusterId = extractClusterId(samples);

        double messagesInPerSec = getAggregateMetric(samples,
                "kafka_server_BrokerTopicMetrics_MessagesInPerSec_OneMinuteRate");
        double bytesInPerSec = getAggregateMetric(samples,
                "kafka_server_BrokerTopicMetrics_BytesInPerSec_OneMinuteRate");
        double bytesOutPerSec = getAggregateMetric(samples,
                "kafka_server_BrokerTopicMetrics_BytesOutPerSec_OneMinuteRate");

        double underReplicatedPartitions = getAggregateMetric(samples,
                "kafka_server_ReplicaManager_UnderReplicatedPartitions");
        double activeControllerCount = getAggregateMetric(samples,
                "kafka_controller_KafkaController_ActiveControllerCount");
        double offlinePartitionsCount = getAggregateMetric(samples,
                "kafka_controller_KafkaController_OfflinePartitionsCount");
        double brokerState = getAggregateMetric(samples,
                "kafka_server_KafkaServer_BrokerState");

        double leaderCount = getAggregateMetric(samples,
                "kafka_server_ReplicaManager_LeaderCount");
        double partitionCount = getAggregateMetric(samples,
                "kafka_server_ReplicaManager_PartitionCount");

        double isrShrinksPerSec = getAggregateMetric(samples,
                "kafka_server_ReplicaManager_IsrShrinksPerSec_OneMinuteRate");
        double isrExpandsPerSec = getAggregateMetric(samples,
                "kafka_server_ReplicaManager_IsrExpandsPerSec_OneMinuteRate");

        double requestHandlerIdle = getAggregateMetric(samples,
                "kafka_server_KafkaRequestHandlerPool_RequestHandlerAvgIdlePercent");

        double heapUsedBytes = getLabeledMetric(samples, "jvm_memory_bytes_used", "area", "heap");
        double heapMaxBytes = getLabeledMetric(samples, "jvm_memory_bytes_max", "area", "heap");

        return new ApiDtos.BrokerMetricsSample(
                target.getId(),
                target.getHost(),
                target.getMetricsPort(),
                target.getRole(),
                discoveredClusterId,
                true,
                null,
                Instant.now(),
                latencyMs,
                messagesInPerSec,
                bytesInPerSec,
                bytesOutPerSec,
                underReplicatedPartitions,
                activeControllerCount,
                offlinePartitionsCount,
                brokerState,
                leaderCount,
                partitionCount,
                isrShrinksPerSec,
                isrExpandsPerSec,
                requestHandlerIdle,
                heapUsedBytes,
                heapMaxBytes
        );
    }

    /**
     * Auto-onboards discovered clusters that don't already exist in the system.
     * Uses the first reachable broker in each cluster group as the bootstrap listener.
     */
    private void autoOnboardDiscoveredClusters(List<ApiDtos.DiscoveredCluster> discoveredClusters,
                                                List<MetricsTarget> targets) {
        for (ApiDtos.DiscoveredCluster dc : discoveredClusters) {
            if (dc.clusterId() == null) continue;

            // Check if a cluster with this JMX cluster ID already exists
            if (clusterService.existsByJmxClusterId(dc.clusterId())) continue;

            ApiDtos.BrokerMetricsSample firstBroker = dc.brokers().stream()
                    .filter(ApiDtos.BrokerMetricsSample::reachable)
                    .findFirst()
                    .orElse(null);
            if (firstBroker == null) continue;

            // Look up clusterName and environment from the matching MetricsTarget
            MetricsTarget matchingTarget = targets.stream()
                    .filter(t -> t.getId().equals(firstBroker.targetId()))
                    .findFirst()
                    .orElse(null);

            String clusterName = matchingTarget != null && matchingTarget.getClusterName() != null
                    ? matchingTarget.getClusterName()
                    : "Discovered: " + dc.clusterId();
            ClusterEnvironment env = matchingTarget != null && "PROD".equals(matchingTarget.getEnvironment())
                    ? ClusterEnvironment.PROD
                    : ClusterEnvironment.NON_PROD;

            try {
                var request = new ApiDtos.CreateClusterRequest(
                        clusterName,
                        env,
                        "Auto-discovered from JMX scrape. Cluster ID: " + dc.clusterId(),
                        List.of(new ApiDtos.ClusterListenerRequest(
                                "jmx-discovered",
                                firstBroker.host(),
                                9092,
                                true,
                                new ApiDtos.AuthProfileRequest(
                                        "plaintext",
                                        AuthProfileType.PLAINTEXT,
                                        "PLAINTEXT",
                                        null, null, null, null, null, null, null, null, null
                                )
                        )),
                        List.of()
                );
                Cluster created = clusterService.createCluster(request, "jmx-auto-discovery");
                created.setJmxClusterId(dc.clusterId());
                clusterService.saveCluster(created);
                log.info("Auto-onboarded cluster '{}' from JMX discovery", clusterName);
            } catch (Exception e) {
                log.warn("Failed to auto-onboard cluster {}: {}", dc.clusterId(), e.getMessage());
            }
        }
    }

    /**
     * Validates that a hostname does not resolve to a restricted address (SSRF prevention).
     * Blocks loopback, link-local, and cloud metadata IPs.
     */
    private boolean isAllowedHost(String host) {
        try {
            InetAddress addr = InetAddress.getByName(host);
            if (addr.isLinkLocalAddress() || addr.isAnyLocalAddress() || addr.isMulticastAddress()) {
                return false;
            }
            // Loopback is blocked in production (SSRF), allowed in dev for local Kafka
            if (addr.isLoopbackAddress() && !allowLoopback) {
                return false;
            }
            // Block cloud metadata endpoints (169.254.169.254 and fd00:ec2::254)
            String ip = addr.getHostAddress();
            if (ip.startsWith("169.254.") || ip.equals("fd00:ec2::254")) {
                return false;
            }
            // RFC 1918 private ranges (10.x, 172.16-31.x, 192.168.x) are intentionally
            // allowed: the core use case is scraping JMX exporters on internal Kafka brokers.
            // Protection relies on: (1) DNS rebinding re-check in scrapeTarget(),
            // (2) cloud metadata IP blocks above, (3) loopback restrictions in SAML mode.
            return true;
        } catch (UnknownHostException e) {
            log.warn("Cannot resolve host '{}': {}", host, e.getMessage());
            return false;
        }
    }

    private ApiDtos.BrokerMetricsSample errorSample(MetricsTarget target, long latencyMs, String errorMessage) {
        return new ApiDtos.BrokerMetricsSample(
                target.getId(),
                target.getHost(),
                target.getMetricsPort(),
                target.getRole(),
                null,   // discoveredClusterId unknown when unreachable
                false,
                errorMessage,
                Instant.now(),
                latencyMs,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1
        );
    }
}
