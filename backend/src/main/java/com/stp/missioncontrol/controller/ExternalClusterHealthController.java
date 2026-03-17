package com.stp.missioncontrol.controller;

import com.stp.missioncontrol.dto.ApiDtos;
import com.stp.missioncontrol.security.ServiceAccountPrincipal;
import com.stp.missioncontrol.service.HealthService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/external/v1/clusters")
public class ExternalClusterHealthController {

    private final HealthService healthService;

    public ExternalClusterHealthController(HealthService healthService) {
        this.healthService = healthService;
    }

    @GetMapping("/health")
    public List<ApiDtos.ClusterHealthSummaryResponse> listClusterHealth(
            @AuthenticationPrincipal ServiceAccountPrincipal principal
    ) {
        return healthService.listClusterHealth(Optional.ofNullable(principal));
    }

    @GetMapping("/{clusterId}/health")
    public ApiDtos.ClusterHealthDetailResponse getClusterHealth(
            @PathVariable UUID clusterId,
            @AuthenticationPrincipal ServiceAccountPrincipal principal
    ) {
        return healthService.getClusterHealth(clusterId, Optional.ofNullable(principal));
    }

    @PostMapping("/{clusterId}/health/refresh")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiDtos.RefreshOperationResponse refreshClusterHealth(
            @PathVariable UUID clusterId,
            @AuthenticationPrincipal ServiceAccountPrincipal principal
    ) {
        return healthService.queueRefresh(clusterId, principal.getName(), Optional.ofNullable(principal));
    }
}
