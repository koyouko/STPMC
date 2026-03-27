package com.stp.missioncontrol.service;

import com.stp.missioncontrol.dto.ApiDtos;
import com.stp.missioncontrol.model.MetricsTarget;
import com.stp.missioncontrol.repository.ClusterRepository;
import com.stp.missioncontrol.repository.MetricsTargetRepository;
import java.io.IOException;
import java.net.URI;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class MetricsScraperService {

    private static final Logger log = LoggerFactory.getLogger(MetricsScraperService.class);
    private static final int SCRAPE_TIMEOUT_MS = 5000;
    private static final Pattern LABEL_PATTERN = Pattern.compile("(\\w+)=\"([^\"]*)\"");

    private final MetricsTargetRepository metricsTargetRepository;
    private final ClusterRepository clusterRepository;
    private final HttpClient httpClient;

    public MetricsScraperService(
            MetricsTargetRepository metricsTargetRepository,
            ClusterRepository clusterRepository
    ) {
        this.metricsTargetRepository = metricsTargetRepository;
        this.clusterRepository = clusterRepository;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(SCRAPE_TIMEOUT_MS))
                .build();
    }

    // ── Inventory management ──────────────────────────────────────────

    @Transactional
    public List<MetricsTarget> uploadInventory(UUID clusterId, MultipartFile file) throws IOException {
        clusterRepository.findById(clusterId)
                .orElseThrow(() -> new IllegalArgumentException("Cluster not found: " + clusterId));

        String content = new String(file.getBytes(), StandardCharsets.UTF_8);
        List<MetricsTarget> targets = parseInventoryCsv(clusterId, content);

        if (targets.isEmpty()) {
            throw new IllegalArgumentException("No valid targets found in the uploaded file");
        }

        metricsTargetRepository.deleteByClusterId(clusterId);
        return metricsTargetRepository.saveAll(targets);
    }

    /**
     * Parses a simple CSV/text inventory file.
     * <pre>
     * # comment lines are ignored
     * # host, port (optional, default 9404), role (optional, default BROKER), label (optional)
     * broker1.internal
     * broker2.internal,9404,BROKER,Broker Two
     * zk1.internal,9404,ZOOKEEPER,ZooKeeper
     * </pre>
     */
    private List<MetricsTarget> parseInventoryCsv(UUID clusterId, String content) {
        List<MetricsTarget> targets = new ArrayList<>();
        int lineNum = 0;
        for (String raw : content.split("\n")) {
            lineNum++;
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;

            String[] parts = line.split(",", -1);
            String host = parts[0].trim();
            if (host.isEmpty()) continue;

            int port = 9404;
            if (parts.length > 1 && !parts[1].trim().isEmpty()) {
                try {
                    port = Integer.parseInt(parts[1].trim());
                } catch (NumberFormatException e) {
                    log.warn("Line {}: invalid port '{}', using 9404", lineNum, parts[1].trim());
                }
            }

            String role = (parts.length > 2 && !parts[2].trim().isEmpty())
                    ? parts[2].trim().toUpperCase()
                    : "BROKER";

            String label = (parts.length > 3 && !parts[3].trim().isEmpty())
                    ? parts[3].trim()
                    : host;

            MetricsTarget target = new MetricsTarget();
            target.setClusterId(clusterId);
            target.setHost(host);
            target.setMetricsPort(port);
            target.setRole(role);
            target.setLabel(label);
            targets.add(target);
        }
        return targets;
    }

    // ── Scraping ──────────────────────────────────────────────────────

    public ApiDtos.ClusterMetricsScrapeResponse scrapeCluster(UUID clusterId) {
        List<MetricsTarget> targets = metricsTargetRepository.findByClusterIdAndEnabledTrue(clusterId);
        Instant scrapedAt = Instant.now();

        List<ApiDtos.BrokerMetricsSample> samples = targets.stream()
                .map(this::scrapeTarget)
                .toList();

        return new ApiDtos.ClusterMetricsScrapeResponse(clusterId, scrapedAt, samples);
    }

    private ApiDtos.BrokerMetricsSample scrapeTarget(MetricsTarget target) {
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
     * Returns the value for a metric without a specific label key,
     * treating that as the aggregate (all-topics) value.
     * Falls back to the first matching sample if no unlabeled version exists.
     */
    private double getAggregateMetric(List<MetricSample> samples, String name) {
        // Prefer sample with no topic label (all-topics aggregate)
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

    /**
     * Extracts the key Confluent Platform 7.9 JMX metrics from a Prometheus scrape.
     * Metric names follow the standard Confluent JMX Prometheus exporter convention.
     */
    private ApiDtos.BrokerMetricsSample extractBrokerMetrics(
            MetricsTarget target, long latencyMs, List<MetricSample> samples) {

        // Throughput
        double messagesInPerSec = getAggregateMetric(samples,
                "kafka_server_BrokerTopicMetrics_MessagesInPerSec_OneMinuteRate");
        double bytesInPerSec = getAggregateMetric(samples,
                "kafka_server_BrokerTopicMetrics_BytesInPerSec_OneMinuteRate");
        double bytesOutPerSec = getAggregateMetric(samples,
                "kafka_server_BrokerTopicMetrics_BytesOutPerSec_OneMinuteRate");

        // Health indicators
        double underReplicatedPartitions = getAggregateMetric(samples,
                "kafka_server_ReplicaManager_UnderReplicatedPartitions");
        double activeControllerCount = getAggregateMetric(samples,
                "kafka_controller_KafkaController_ActiveControllerCount");
        double offlinePartitionsCount = getAggregateMetric(samples,
                "kafka_controller_KafkaController_OfflinePartitionsCount");
        double brokerState = getAggregateMetric(samples,
                "kafka_server_KafkaServer_BrokerState");

        // Capacity
        double leaderCount = getAggregateMetric(samples,
                "kafka_server_ReplicaManager_LeaderCount");
        double partitionCount = getAggregateMetric(samples,
                "kafka_server_ReplicaManager_PartitionCount");

        // ISR churn
        double isrShrinksPerSec = getAggregateMetric(samples,
                "kafka_server_ReplicaManager_IsrShrinksPerSec_OneMinuteRate");
        double isrExpandsPerSec = getAggregateMetric(samples,
                "kafka_server_ReplicaManager_IsrExpandsPerSec_OneMinuteRate");

        // Request handler pool idle ratio (0..1, lower is busier)
        double requestHandlerIdle = getAggregateMetric(samples,
                "kafka_server_KafkaRequestHandlerPool_RequestHandlerAvgIdlePercent");

        // JVM heap (filtered by area="heap")
        double heapUsedBytes = getLabeledMetric(samples, "jvm_memory_bytes_used", "area", "heap");
        double heapMaxBytes = getLabeledMetric(samples, "jvm_memory_bytes_max", "area", "heap");

        return new ApiDtos.BrokerMetricsSample(
                target.getId(),
                target.getHost(),
                target.getMetricsPort(),
                target.getRole(),
                target.getLabel(),
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

    private ApiDtos.BrokerMetricsSample errorSample(MetricsTarget target, long latencyMs, String errorMessage) {
        return new ApiDtos.BrokerMetricsSample(
                target.getId(),
                target.getHost(),
                target.getMetricsPort(),
                target.getRole(),
                target.getLabel(),
                false,
                errorMessage,
                Instant.now(),
                latencyMs,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1
        );
    }
}
