package com.stp.missioncontrol.controller;

import com.stp.missioncontrol.dto.ApiDtos;
import com.stp.missioncontrol.service.ClusterService;
import com.stp.missioncontrol.service.HealthService;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/platform/clusters")
public class PlatformClusterController {

    private final ClusterService clusterService;
    private final HealthService healthService;

    public PlatformClusterController(ClusterService clusterService, HealthService healthService) {
        this.clusterService = clusterService;
        this.healthService = healthService;
    }

    @GetMapping
    public List<ApiDtos.ClusterHealthSummaryResponse> listClusters() {
        return healthService.listClusterHealth(Optional.empty());
    }

    @GetMapping("/{clusterId}/health")
    public ApiDtos.ClusterHealthDetailResponse getClusterHealth(@PathVariable UUID clusterId) {
        return healthService.getClusterHealth(clusterId, Optional.empty());
    }

    @PostMapping("/{clusterId}/health/refresh")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiDtos.RefreshOperationResponse refreshClusterHealth(
            @PathVariable UUID clusterId,
            Principal principal
    ) {
        return healthService.queuePlatformRefresh(clusterId, principal.getName());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiDtos.ClusterHealthDetailResponse createCluster(
            @Valid @RequestBody ApiDtos.CreateClusterRequest request,
            Principal principal
    ) {
        var cluster = clusterService.createCluster(request, principal.getName());
        return healthService.toDetail(cluster);
    }

    @GetMapping("/{clusterId}/config")
    public ApiDtos.ClusterConfigResponse getClusterConfig(@PathVariable UUID clusterId) {
        return clusterService.getClusterConfig(clusterId);
    }

    @PutMapping("/{clusterId}")
    public ApiDtos.ClusterHealthDetailResponse updateCluster(
            @PathVariable UUID clusterId,
            @Valid @RequestBody ApiDtos.UpdateClusterRequest request,
            Principal principal
    ) {
        var cluster = clusterService.updateCluster(clusterId, request, principal.getName());
        return healthService.toDetail(cluster);
    }

    @DeleteMapping("/{clusterId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCluster(@PathVariable UUID clusterId, Principal principal) {
        clusterService.deleteCluster(clusterId, principal.getName());
    }
}
