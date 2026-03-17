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
        return clusterRepository.findAll();
    }

    public boolean hasClusters() {
        return !clusterRepository.findAll().isEmpty();
    }

    public boolean clusterNameExists(String name) {
        return clusterRepository.findAll().stream()
                .anyMatch(cluster -> cluster.getName().equalsIgnoreCase(name));
    }

    public Cluster getCluster(UUID clusterId) {
        return clusterRepository.findDetailedById(clusterId)
                .orElseThrow(() -> new IllegalArgumentException("Cluster not found"));
    }
}
