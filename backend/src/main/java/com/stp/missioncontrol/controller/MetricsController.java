package com.stp.missioncontrol.controller;

import com.stp.missioncontrol.dto.ApiDtos;
import com.stp.missioncontrol.model.MetricsTarget;
import com.stp.missioncontrol.repository.MetricsTargetRepository;
import com.stp.missioncontrol.service.MetricsScraperService;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
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
@RequestMapping("/api/platform/clusters/{clusterId}/metrics")
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
     * Upload a CSV inventory file to replace all metrics targets for this cluster.
     * Format: host, port (optional, default 9404), role (optional), label (optional)
     * Lines starting with '#' are treated as comments.
     */
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN', 'OPERATOR')")
    @PostMapping("/targets/upload")
    public List<ApiDtos.MetricsTargetResponse> uploadInventory(
            @PathVariable UUID clusterId,
            @RequestParam("file") MultipartFile file) throws IOException {

        if (file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file is empty");
        }

        List<MetricsTarget> targets = metricsScraperService.uploadInventory(clusterId, file);
        return targets.stream().map(this::toResponse).toList();
    }

    /** List all enabled metrics targets for this cluster. */
    @GetMapping("/targets")
    public List<ApiDtos.MetricsTargetResponse> listTargets(@PathVariable UUID clusterId) {
        return metricsTargetRepository.findByClusterIdAndEnabledTrue(clusterId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /** Delete a specific metrics target. */
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN', 'OPERATOR')")
    @DeleteMapping("/targets/{targetId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTarget(@PathVariable UUID clusterId, @PathVariable UUID targetId) {
        metricsTargetRepository.findById(targetId).ifPresent(target -> {
            if (!target.getClusterId().equals(clusterId)) {
                throw new IllegalArgumentException("Target does not belong to this cluster");
            }
            metricsTargetRepository.deleteById(targetId);
        });
    }

    /**
     * On-demand scrape: fetch fresh metrics from all configured targets and return them.
     * Each target is scraped sequentially; unreachable targets are included with reachable=false.
     */
    @GetMapping("/scrape")
    public ApiDtos.ClusterMetricsScrapeResponse scrapeCluster(@PathVariable UUID clusterId) {
        return metricsScraperService.scrapeCluster(clusterId);
    }

    private ApiDtos.MetricsTargetResponse toResponse(MetricsTarget target) {
        return new ApiDtos.MetricsTargetResponse(
                target.getId(),
                target.getHost(),
                target.getMetricsPort(),
                target.getRole(),
                target.getLabel(),
                target.isEnabled(),
                target.getCreatedAt()
        );
    }
}
