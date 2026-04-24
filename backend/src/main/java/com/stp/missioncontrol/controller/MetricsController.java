package com.stp.missioncontrol.controller;

import com.stp.missioncontrol.dto.ApiDtos;
import com.stp.missioncontrol.model.MetricsTarget;
import com.stp.missioncontrol.repository.MetricsTargetRepository;
import com.stp.missioncontrol.service.MetricsScraperService;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/platform/metrics")
public class MetricsController {

    private final MetricsScraperService metricsScraperService;
    private final MetricsTargetRepository metricsTargetRepository;

    public MetricsController(
            MetricsScraperService metricsScraperService,
            MetricsTargetRepository metricsTargetRepository
    ) {
        this.metricsScraperService = metricsScraperService;
        this.metricsTargetRepository = metricsTargetRepository;
    }

    /**
     * Upload a CSV inventory file to replace the entire global target list.
     * Format: host, port (optional, default 9404), role (optional), label (optional)
     * Lines starting with '#' are treated as comments.
     */
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN', 'OPERATOR')")
    @PostMapping("/targets/upload")
    public List<ApiDtos.MetricsTargetResponse> uploadInventory(
            @RequestParam("file") MultipartFile file) throws IOException {

        if (file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file is empty");
        }

        List<MetricsTarget> targets = metricsScraperService.uploadInventory(file);
        return targets.stream().map(this::toResponse).toList();
    }

    /** List all enabled metrics targets. */
    @GetMapping("/targets")
    public List<ApiDtos.MetricsTargetResponse> listTargets() {
        return metricsTargetRepository.findByEnabledTrue()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /** Delete a specific metrics target. */
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN', 'OPERATOR')")
    @DeleteMapping("/targets/{targetId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTarget(@PathVariable UUID targetId) {
        metricsTargetRepository.deleteById(targetId);
    }

    /**
     * On-demand scrape: fetch fresh metrics from all configured targets, extract cluster IDs
     * from JMX, and return results grouped by discovered cluster UUID.
     */
    @GetMapping("/scrape")
    public ApiDtos.MetricsScrapeResponse scrapeAll() {
        return metricsScraperService.scrapeAll();
    }

    /**
     * Returns the most recent in-memory scrape snapshot. 204 if no scrape has
     * been run since process startup. Lets the sidebar and cluster-detail
     * pages render broker inventory without triggering a fresh scrape.
     */
    @GetMapping("/last-scrape")
    public ResponseEntity<ApiDtos.MetricsScrapeResponse> lastScrape() {
        return metricsScraperService.getLastScrape()
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /** Client-facing scraper configuration (auto-scrape interval + per-request timeout). */
    @GetMapping("/config")
    public java.util.Map<String, Object> config() {
        return java.util.Map.of(
                "scrapeIntervalMs", metricsScraperService.getScrapeIntervalMs(),
                "scrapeTimeoutMs", metricsScraperService.getScrapeTimeoutMs()
        );
    }

    /**
     * Diagnostic: return the raw Prometheus text from a single target's JMX
     * exporter. Useful when some metrics aren't populating on the UI - the
     * operator can see exactly what the exporter emits and confirm whether
     * the metric names / label shape match what the scraper looks up.
     *
     * <pre>curl http://$host:8080/api/platform/metrics/targets/$TARGET_ID/raw | grep -i messagesin</pre>
     */
    @GetMapping(value = "/targets/{targetId}/raw", produces = "text/plain")
    public ResponseEntity<String> rawScrape(@PathVariable UUID targetId) {
        return metricsScraperService.scrapeRawBody(targetId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private ApiDtos.MetricsTargetResponse toResponse(MetricsTarget target) {
        return new ApiDtos.MetricsTargetResponse(
                target.getId(),
                target.getClusterName(),
                target.getDiscoveredClusterId(),
                target.getHost(),
                target.getMetricsPort(),
                target.getRole(),
                target.getEnvironment(),
                target.isEnabled(),
                target.getCreatedAt()
        );
    }
}
