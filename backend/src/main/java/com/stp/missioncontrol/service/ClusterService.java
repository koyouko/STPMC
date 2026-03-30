package com.stp.missioncontrol.service;

import com.stp.missioncontrol.dto.ApiDtos;
import com.stp.missioncontrol.model.Cluster;
import com.stp.missioncontrol.model.ClusterAuthProfile;
import com.stp.missioncontrol.model.ClusterHealthSnapshot;
import com.stp.missioncontrol.model.ClusterListener;
import com.stp.missioncontrol.model.ServiceEndpoint;
import com.stp.missioncontrol.repository.ClusterRepository;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ClusterService {

    private final ClusterRepository clusterRepository;
    private final AuditService auditService;

    public ClusterService(ClusterRepository clusterRepository, AuditService auditService) {
        this.clusterRepository = clusterRepository;
        this.auditService = auditService;
    }

    @Transactional
    public Cluster createCluster(ApiDtos.CreateClusterRequest request, String actor) {
        Cluster cluster = new Cluster(request.name(), request.environment(), request.description());

        request.listeners().forEach(listenerRequest -> {
            var auth = new ClusterAuthProfile(
                    listenerRequest.authProfile().name(),
                    listenerRequest.authProfile().type(),
                    listenerRequest.authProfile().securityProtocol()
            );
            auth.setTruststorePath(listenerRequest.authProfile().truststorePath());
            auth.setTruststorePasswordFile(listenerRequest.authProfile().truststorePasswordFile());
            auth.setKeystorePath(listenerRequest.authProfile().keystorePath());
            auth.setKeystorePasswordFile(listenerRequest.authProfile().keystorePasswordFile());
            auth.setKeyPasswordFile(listenerRequest.authProfile().keyPasswordFile());
            auth.setPrincipal(listenerRequest.authProfile().principal());
            auth.setKeytabPath(listenerRequest.authProfile().keytabPath());
            auth.setKrb5ConfigPath(listenerRequest.authProfile().krb5ConfigPath());
            auth.setSaslServiceName(listenerRequest.authProfile().saslServiceName());
            cluster.addListener(new ClusterListener(
                    listenerRequest.name(),
                    listenerRequest.host(),
                    listenerRequest.port(),
                    listenerRequest.preferred(),
                    auth
            ));
        });

        if (request.serviceEndpoints() != null) {
            request.serviceEndpoints().forEach(endpointRequest -> cluster.addServiceEndpoint(new ServiceEndpoint(
                    endpointRequest.kind(),
                    endpointRequest.protocol(),
                    endpointRequest.baseUrl(),
                    endpointRequest.host(),
                    endpointRequest.port(),
                    endpointRequest.healthPath(),
                    endpointRequest.version()
            )));
        }

        cluster.setHealthSnapshot(new ClusterHealthSnapshot(cluster));
        Cluster saved = clusterRepository.save(cluster);
        auditService.record(actor, "CLUSTER_CREATED", "Cluster", saved.getId().toString(), saved.getName());
        return saved;
    }

    public List<Cluster> listClusters() {
        return clusterRepository.findByActiveTrue();
    }

    public boolean hasClusters() {
        return clusterRepository.existsByActiveTrue();
    }

    public boolean clusterNameExists(String name) {
        return clusterRepository.existsByActiveTrueAndNameIgnoreCase(name);
    }

    public boolean existsByJmxClusterId(String jmxClusterId) {
        return clusterRepository.existsByJmxClusterIdAndActiveTrue(jmxClusterId);
    }

    @Transactional
    public void saveCluster(Cluster cluster) {
        clusterRepository.save(cluster);
    }

    public Cluster getCluster(UUID clusterId) {
        return clusterRepository.findDetailedById(clusterId)
                .orElseThrow(() -> new IllegalArgumentException("Cluster not found"));
    }

    @Transactional
    public Cluster updateCluster(UUID clusterId, ApiDtos.UpdateClusterRequest request, String actor) {
        Cluster cluster = getCluster(clusterId);

        if (request.name() != null && !request.name().isBlank()) {
            if (!cluster.getName().equalsIgnoreCase(request.name()) && clusterNameExists(request.name())) {
                throw new IllegalArgumentException("A cluster with name '" + request.name() + "' already exists");
            }
            cluster.setName(request.name());
        }
        if (request.description() != null) {
            cluster.setDescription(request.description());
        }
        if (request.environment() != null) {
            cluster.setEnvironment(request.environment());
        }

        if (request.listeners() != null) {
            cluster.clearListeners();
            request.listeners().forEach(listenerRequest -> {
                var auth = new ClusterAuthProfile(
                        listenerRequest.authProfile().name(),
                        listenerRequest.authProfile().type(),
                        listenerRequest.authProfile().securityProtocol()
                );
                auth.setTruststorePath(listenerRequest.authProfile().truststorePath());
                auth.setTruststorePasswordFile(listenerRequest.authProfile().truststorePasswordFile());
                auth.setKeystorePath(listenerRequest.authProfile().keystorePath());
                auth.setKeystorePasswordFile(listenerRequest.authProfile().keystorePasswordFile());
                auth.setKeyPasswordFile(listenerRequest.authProfile().keyPasswordFile());
                auth.setPrincipal(listenerRequest.authProfile().principal());
                auth.setKeytabPath(listenerRequest.authProfile().keytabPath());
                auth.setKrb5ConfigPath(listenerRequest.authProfile().krb5ConfigPath());
                auth.setSaslServiceName(listenerRequest.authProfile().saslServiceName());
                cluster.addListener(new ClusterListener(
                        listenerRequest.name(),
                        listenerRequest.host(),
                        listenerRequest.port(),
                        listenerRequest.preferred(),
                        auth
                ));
            });
        }

        if (request.serviceEndpoints() != null) {
            cluster.clearServiceEndpoints();
            request.serviceEndpoints().forEach(endpointRequest -> cluster.addServiceEndpoint(new ServiceEndpoint(
                    endpointRequest.kind(),
                    endpointRequest.protocol(),
                    endpointRequest.baseUrl(),
                    endpointRequest.host(),
                    endpointRequest.port(),
                    endpointRequest.healthPath(),
                    endpointRequest.version()
            )));
        }

        Cluster saved = clusterRepository.save(cluster);
        auditService.record(actor, "CLUSTER_UPDATED", "Cluster", saved.getId().toString(), saved.getName());
        return saved;
    }

    @Transactional
    public void deleteCluster(UUID clusterId, String actor) {
        Cluster cluster = getCluster(clusterId);
        cluster.setActive(false);
        clusterRepository.save(cluster);
        auditService.record(actor, "CLUSTER_DEACTIVATED", "Cluster", clusterId.toString(), cluster.getName());
    }

    public ApiDtos.ClusterConfigResponse getClusterConfig(UUID clusterId) {
        Cluster cluster = getCluster(clusterId);
        return new ApiDtos.ClusterConfigResponse(
                cluster.getId(),
                cluster.getName(),
                cluster.getDescription(),
                cluster.getEnvironment(),
                cluster.getConnectionMode(),
                cluster.isActive(),
                cluster.getCreatedAt(),
                cluster.getUpdatedAt(),
                cluster.getListeners().stream().map(listener -> new ApiDtos.ClusterListenerResponse(
                        listener.getId(),
                        listener.getName(),
                        listener.getHost(),
                        listener.getPort(),
                        listener.isPreferred(),
                        toAuthProfileResponse(listener.getAuthProfile())
                )).toList(),
                cluster.getServiceEndpoints().stream().map(endpoint -> new ApiDtos.ServiceEndpointResponse(
                        endpoint.getId(),
                        endpoint.getKind(),
                        endpoint.getProtocol(),
                        endpoint.getBaseUrl(),
                        endpoint.getHost(),
                        endpoint.getPort(),
                        endpoint.getHealthPath(),
                        endpoint.getVersion(),
                        endpoint.isEnabled()
                )).toList()
        );
    }

    private ApiDtos.AuthProfileResponse toAuthProfileResponse(ClusterAuthProfile profile) {
        return new ApiDtos.AuthProfileResponse(
                profile.getId(),
                profile.getName(),
                profile.getType(),
                profile.getSecurityProtocol(),
                profile.getTruststorePath(),
                profile.getKeystorePath(),
                profile.getPrincipal(),
                profile.getKeytabPath(),
                profile.getKrb5ConfigPath(),
                profile.getSaslServiceName()
        );
    }
}
