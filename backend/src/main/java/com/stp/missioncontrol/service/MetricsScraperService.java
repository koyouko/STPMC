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
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
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

    // JMX metric that carries the Kafka cluster UUID as a label value.
    // Names are stored lowercase at parse time because the Confluent kafka_broker.yml
    // sets `lowercaseOutputName: true` and `lowercaseOutputLabelNames: true`,
    // which lowercases every metric name and label key (values stay as-is).
    private static final String CLUSTER_ID_METRIC = "kafka_server_kafkaserver_clusterid";
    private static final String CLUSTER_ID_LABEL = "clusterid";

    private final MetricsTargetRepository metricsTargetRepository;
    private final ClusterService clusterService;
    private final HttpClient httpClient;
    private final boolean allowLoopback;
    private final ExecutorService metricsScraperExecutor;
    private final long scrapeIntervalMs;
    /** In-memory cache of the most recent {@link #scrapeAll()} result.
     *  Read path ({@code GET /last-scrape}) lets the UI render sidebar
     *  broker lists and the cluster-page inventory panel without triggering
     *  a fresh scrape on every page load. Cleared only by process restart. */
    private final AtomicReference<ApiDtos.MetricsScrapeResponse> lastScrape = new AtomicReference<>();

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
        this.scrapeIntervalMs = properties.metrics().scrapeIntervalMs();
    }

    /**
     * Background auto-scrape. Runs every {@code app.metrics.scrape-interval-ms}
     * milliseconds (0 disables). Uses {@code fixedDelayString}, so the next
     * run waits for the previous one to complete — large target sets never
     * stampede. First run is delayed by
     * {@code app.metrics.scrape-initial-delay-ms} to let the app settle.
     *
     * <p>Skips quietly when no targets are configured, when the interval is
     * {@code <= 0}, or when any exception occurs (logged at WARN; next tick
     * retries normally).
     */
    @Scheduled(
            fixedDelayString = "${app.metrics.scrape-interval-ms:60000}",
            initialDelayString = "${app.metrics.scrape-initial-delay-ms:30000}"
    )
    public void scheduledScrape() {
        if (scrapeIntervalMs <= 0) return;
        try {
            long targetCount = metricsTargetRepository.count();
            if (targetCount == 0) return;
            log.info("Scheduled scrape starting ({} targets)", targetCount);
            scrapeAll();
        } catch (Exception e) {
            log.warn("Scheduled scrape failed: {}", e.getMessage());
        }
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
            // Strip URL scheme if present (e.g., "http://hostname" → "hostname")
            if (host != null) {
                host = host.replaceFirst("^https?://", "");
                // Also strip trailing path or port if pasted as full URL
                host = host.split("[:/]")[0];
            }
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
                environment = parts[4].trim().toUpperCase();
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

        // Group by the CSV-provided clusterName. JMX-derived cluster ID is kept
        // per-broker as a diagnostic but is no longer the grouping key, because
        // the typical Confluent kafka_broker.yml does not expose ClusterId as a
        // labeled metric (it is a String JMX attribute and requires a specific
        // value/label rule). Name-based grouping works as soon as the CSV is
        // uploaded, regardless of exporter configuration.
        Map<UUID, String> targetIdToClusterName = targets.stream()
                .collect(Collectors.toMap(
                        MetricsTarget::getId,
                        t -> t.getClusterName() != null ? t.getClusterName() : "",
                        (a, b) -> a,
                        LinkedHashMap::new
                ));

        Map<String, List<ApiDtos.BrokerMetricsSample>> grouped = samples.stream()
                .collect(Collectors.groupingBy(
                        s -> {
                            String name = targetIdToClusterName.get(s.targetId());
                            return name != null ? name : "";
                        },
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        List<ApiDtos.DiscoveredCluster> clusters = grouped.entrySet().stream()
                .map(e -> {
                    String groupName = e.getKey().isEmpty() ? null : e.getKey();
                    // Prefer a non-null JMX cluster ID from any broker in this group,
                    // so the UI can display it as a secondary identifier if present.
                    String jmxId = e.getValue().stream()
                            .map(ApiDtos.BrokerMetricsSample::discoveredClusterId)
                            .filter(s -> s != null && !s.isBlank())
                            .findFirst()
                            .orElse(null);
                    return new ApiDtos.DiscoveredCluster(groupName, jmxId, e.getValue());
                })
                .toList();

        // Write back discoveredClusterId to each target and persist
        writeBackDiscoveredClusterIds(targets, samples);

        // Auto-onboard: for each discovered cluster ID, create a Cluster entry
        // if one doesn't already exist, using the first reachable broker as bootstrap
        autoOnboardDiscoveredClusters(clusters, targets);

        ApiDtos.MetricsScrapeResponse response = new ApiDtos.MetricsScrapeResponse(scrapedAt, clusters);
        lastScrape.set(response);
        return response;
    }

    /**
     * Returns the most recent scrape result produced by {@link #scrapeAll()},
     * or empty if no scrape has been performed since process startup.
     * Used by the sidebar and cluster-detail pages to render broker inventory
     * without triggering a fresh scrape on every navigation.
     */
    public Optional<ApiDtos.MetricsScrapeResponse> getLastScrape() {
        return Optional.ofNullable(lastScrape.get());
    }

    /** Configured background auto-scrape interval in ms; 0 when disabled. */
    public long getScrapeIntervalMs() {
        return scrapeIntervalMs;
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
        String baseUrl = "http://" + target.getHost() + ":" + target.getMetricsPort();
        Instant start = Instant.now();
        try {
            // Try root path first (JMX exporter Java agent default), fall back to /metrics
            HttpResponse<String> response = scrapeUrl(baseUrl + "/");
            if (response.statusCode() == 404) {
                response = scrapeUrl(baseUrl + "/metrics");
            }
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

    private HttpResponse<String> scrapeUrl(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .GET()
                .timeout(Duration.ofMillis(SCRAPE_TIMEOUT_MS))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
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
                    // Normalize metric name to lowercase so lookups match regardless
                    // of whether the exporter was configured with lowercaseOutputName.
                    samples.add(new MetricSample(metricName.toLowerCase(), labels, value));
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
            // Lowercase label keys; keep values as-is (label values carry data like "heap")
            labels.put(matcher.group(1).toLowerCase(), matcher.group(2));
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
                .filter(s -> s.name().equals(CLUSTER_ID_METRIC) && s.labels().containsKey(CLUSTER_ID_LABEL))
                .findFirst()
                .map(s -> s.labels().get(CLUSTER_ID_LABEL))
                .orElse(null);
    }

    /**
     * Returns the value for a metric without a topic label (all-topics aggregate).
     * Accepts a list of candidate names (e.g. with and without a {@code _total} suffix,
     * which some JMX exporter rule sets append). Returns the first match.
     */
    private double getAggregateMetric(List<MetricSample> samples, String... candidateNames) {
        for (String name : candidateNames) {
            var match = samples.stream()
                    .filter(s -> s.name().equals(name) && !s.labels().containsKey("topic"))
                    .findFirst()
                    .or(() -> samples.stream()
                            .filter(s -> s.name().equals(name))
                            .findFirst());
            if (match.isPresent()) return match.get().value();
        }
        return -1.0;
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

        // All metric names here are lowercase because parsePrometheusText() lowercases
        // at parse time. Some Confluent kafka_broker.yml rules append a _total suffix
        // (e.g. requesthandleravgidlepercent_total), so we pass candidate variants.
        double messagesInPerSec = getAggregateMetric(samples,
                "kafka_server_brokertopicmetrics_messagesinpersec_oneminuterate");
        double bytesInPerSec = getAggregateMetric(samples,
                "kafka_server_brokertopicmetrics_bytesinpersec_oneminuterate");
        double bytesOutPerSec = getAggregateMetric(samples,
                "kafka_server_brokertopicmetrics_bytesoutpersec_oneminuterate");

        double underReplicatedPartitions = getAggregateMetric(samples,
                "kafka_server_replicamanager_underreplicatedpartitions",
                "kafka_server_replicamanager_underreplicatedpartitions_value");
        double activeControllerCount = getAggregateMetric(samples,
                "kafka_controller_kafkacontroller_activecontrollercount",
                "kafka_controller_kafkacontroller_activecontrollercount_value");
        double offlinePartitionsCount = getAggregateMetric(samples,
                "kafka_controller_kafkacontroller_offlinepartitionscount",
                "kafka_controller_kafkacontroller_offlinepartitionscount_value");
        double brokerState = getAggregateMetric(samples,
                "kafka_server_kafkaserver_brokerstate",
                "kafka_server_kafkaserver_brokerstate_value");

        double leaderCount = getAggregateMetric(samples,
                "kafka_server_replicamanager_leadercount",
                "kafka_server_replicamanager_leadercount_value");
        double partitionCount = getAggregateMetric(samples,
                "kafka_server_replicamanager_partitioncount",
                "kafka_server_replicamanager_partitioncount_value");

        double isrShrinksPerSec = getAggregateMetric(samples,
                "kafka_server_replicamanager_isrshrinkspersec_oneminuterate");
        double isrExpandsPerSec = getAggregateMetric(samples,
                "kafka_server_replicamanager_isrexpandspersec_oneminuterate");

        double requestHandlerIdle = getAggregateMetric(samples,
                "kafka_server_kafkarequesthandlerpool_requesthandleravgidlepercent_total",
                "kafka_server_kafkarequesthandlerpool_requesthandleravgidlepercent");

        double heapUsedBytes = getLabeledMetric(samples, "jvm_memory_bytes_used", "area", "heap");
        double heapMaxBytes = getLabeledMetric(samples, "jvm_memory_bytes_max", "area", "heap");

        // Uptime — Prometheus JMX exporter exposes the JVM process start time
        // (unix seconds). Convert to elapsed seconds; -1 if the metric wasn't
        // found (e.g. non-JMX-exporter target).
        double processStartTimeSec = getAggregateMetric(samples, "process_start_time_seconds");
        double uptimeSeconds = processStartTimeSec > 0
                ? Math.max(0, (double) Instant.now().getEpochSecond() - processStartTimeSec)
                : -1;

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
                heapMaxBytes,
                uptimeSeconds
        );
    }

    /**
     * Auto-onboards discovered clusters that don't already exist in the system.
     *
     * <p>Identity rule: prefer the JMX-derived cluster ID when present; otherwise
     * fall back to the CSV-provided clusterName. This lets auto-onboarding work
     * even when the Prometheus JMX exporter doesn't expose
     * {@code kafka_server_KafkaServer_ClusterId} (a String JMX attribute that
     * requires an explicit value/label rule in kafka_broker.yml).
     *
     * <p>Groups with neither a JMX cluster ID nor a clusterName are skipped.
     */
    private void autoOnboardDiscoveredClusters(List<ApiDtos.DiscoveredCluster> discoveredClusters,
                                                List<MetricsTarget> targets) {
        for (ApiDtos.DiscoveredCluster dc : discoveredClusters) {
            // Identity: JMX cluster ID preferred; clusterName is the fallback.
            if (dc.clusterId() == null && dc.clusterName() == null) continue;

            boolean alreadyExists = dc.clusterId() != null
                    ? clusterService.existsByJmxClusterId(dc.clusterId())
                    : clusterService.clusterNameExists(dc.clusterName());
            if (alreadyExists) continue;

            ApiDtos.BrokerMetricsSample firstBroker = dc.brokers().stream()
                    .filter(ApiDtos.BrokerMetricsSample::reachable)
                    .findFirst()
                    .orElse(null);
            if (firstBroker == null) continue;

            MetricsTarget matchingTarget = targets.stream()
                    .filter(t -> t.getId().equals(firstBroker.targetId()))
                    .findFirst()
                    .orElse(null);

            String clusterName = dc.clusterName() != null
                    ? dc.clusterName()
                    : (matchingTarget != null && matchingTarget.getClusterName() != null
                            ? matchingTarget.getClusterName()
                            : "Discovered: " + dc.clusterId());
            // Map target environment to binary cluster classification:
            // "PROD" → PROD, everything else (DEV, SIT, UAT, PTE, etc.) → NON_PROD
            ClusterEnvironment env = matchingTarget != null && "PROD".equalsIgnoreCase(matchingTarget.getEnvironment())
                    ? ClusterEnvironment.PROD
                    : ClusterEnvironment.NON_PROD;

            String notes = dc.clusterId() != null
                    ? "Auto-discovered from JMX scrape. Cluster ID: " + dc.clusterId()
                    : "Auto-discovered from scrape. Grouped by CSV clusterName (no JMX cluster ID exposed).";

            try {
                var request = new ApiDtos.CreateClusterRequest(
                        clusterName,
                        env,
                        notes,
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
                if (dc.clusterId() != null) {
                    created.setJmxClusterId(dc.clusterId());
                    clusterService.saveCluster(created);
                }
                log.info("Auto-onboarded cluster '{}' (jmxClusterId={})",
                        clusterName, dc.clusterId() != null ? dc.clusterId() : "<none>");
            } catch (Exception e) {
                log.warn("Failed to auto-onboard cluster '{}' (jmxClusterId={}): {}",
                        clusterName, dc.clusterId(), e.getMessage());
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
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1
        );
    }
}
