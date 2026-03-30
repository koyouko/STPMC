package com.stp.missioncontrol.validation;

import com.stp.missioncontrol.dto.ApiDtos;
import com.stp.missioncontrol.model.MissionControlEnums.AuthProfileType;
import com.stp.missioncontrol.repository.ClusterRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ClusterValidator {

    private final ClusterRepository clusterRepository;

    public ClusterValidator(ClusterRepository clusterRepository) {
        this.clusterRepository = clusterRepository;
    }

    public List<String> validateCreate(ApiDtos.CreateClusterRequest request) {
        List<String> errors = new ArrayList<>();

        if (nameExists(request.name(), null)) {
            errors.add("A cluster with name '" + request.name() + "' already exists");
        }

        if (request.listeners() == null || request.listeners().isEmpty()) {
            errors.add("At least one Kafka listener is required");
        } else {
            for (ApiDtos.ClusterListenerRequest listener : request.listeners()) {
                errors.addAll(validateListener(listener));
            }
        }

        if (request.serviceEndpoints() != null) {
            for (ApiDtos.ServiceEndpointRequest endpoint : request.serviceEndpoints()) {
                errors.addAll(validateServiceEndpoint(endpoint));
            }
        }

        return errors;
    }

    public List<String> validateUpdate(UUID clusterId, ApiDtos.UpdateClusterRequest request) {
        List<String> errors = new ArrayList<>();

        if (request.name() != null && !request.name().isBlank() && nameExists(request.name(), clusterId)) {
            errors.add("A cluster with name '" + request.name() + "' already exists");
        }

        if (request.listeners() != null) {
            if (request.listeners().isEmpty()) {
                errors.add("At least one Kafka listener is required");
            } else {
                for (ApiDtos.ClusterListenerRequest listener : request.listeners()) {
                    errors.addAll(validateListener(listener));
                }
            }
        }

        if (request.serviceEndpoints() != null) {
            for (ApiDtos.ServiceEndpointRequest endpoint : request.serviceEndpoints()) {
                errors.addAll(validateServiceEndpoint(endpoint));
            }
        }

        return errors;
    }

    private List<String> validateListener(ApiDtos.ClusterListenerRequest listener) {
        List<String> errors = new ArrayList<>();
        if (listener.host() == null || listener.host().isBlank()) {
            errors.add("Listener host is required");
        }
        if (listener.port() <= 0 || listener.port() > 65535) {
            errors.add("Listener port must be between 1 and 65535");
        }
        if (listener.authProfile() != null) {
            errors.addAll(validateAuthProfile(listener.authProfile()));
        }
        return errors;
    }

    private List<String> validateAuthProfile(ApiDtos.AuthProfileRequest profile) {
        List<String> errors = new ArrayList<>();
        if (profile.type() == AuthProfileType.MTLS_SSL) {
            if (isBlank(profile.keystorePath())) {
                errors.add("Keystore path is required for mTLS auth");
            }
        }
        if (profile.type() == AuthProfileType.SASL_GSSAPI) {
            if (isBlank(profile.keytabPath())) {
                errors.add("Keytab path is required for Kerberos auth");
            }
            if (isBlank(profile.principal())) {
                errors.add("Principal is required for Kerberos auth");
            }
        }
        return errors;
    }

    private List<String> validateServiceEndpoint(ApiDtos.ServiceEndpointRequest endpoint) {
        List<String> errors = new ArrayList<>();
        switch (endpoint.protocol()) {
            case HTTP, HTTPS -> {
                if (isBlank(endpoint.baseUrl())) {
                    errors.add(endpoint.kind() + " endpoint requires a base URL for HTTP/HTTPS protocol");
                }
            }
            case TCP -> {
                if (isBlank(endpoint.host())) {
                    errors.add(endpoint.kind() + " endpoint requires a host for TCP protocol");
                }
                if (endpoint.port() == null || endpoint.port() <= 0 || endpoint.port() > 65535) {
                    errors.add(endpoint.kind() + " endpoint requires a valid port for TCP protocol");
                }
            }
        }
        return errors;
    }

    private boolean nameExists(String name, UUID excludeClusterId) {
        return clusterRepository.findByActiveTrue().stream()
                .filter(cluster -> excludeClusterId == null || !cluster.getId().equals(excludeClusterId))
                .anyMatch(cluster -> cluster.getName().equalsIgnoreCase(name));
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
