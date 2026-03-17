package com.stp.missioncontrol.model;

import com.stp.missioncontrol.model.MissionControlEnums.ComponentKind;
import com.stp.missioncontrol.model.MissionControlEnums.ServiceEndpointProtocol;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "service_endpoints")
public class ServiceEndpoint {

    @Id
    private UUID id;

    @ManyToOne(optional = false)
    private Cluster cluster;

    @Enumerated(EnumType.STRING)
    private ComponentKind kind;

    @Enumerated(EnumType.STRING)
    private ServiceEndpointProtocol protocol;

    private String baseUrl;

    private String host;

    private Integer port;

    private String healthPath;

    private String version;

    private boolean enabled;

    public ServiceEndpoint() {
    }

    public ServiceEndpoint(ComponentKind kind, ServiceEndpointProtocol protocol, String baseUrl, String host, Integer port, String healthPath, String version) {
        this.id = UUID.randomUUID();
        this.kind = kind;
        this.protocol = protocol;
        this.baseUrl = baseUrl;
        this.host = host;
        this.port = port;
        this.healthPath = healthPath;
        this.version = version;
        this.enabled = true;
    }

    @PrePersist
    public void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
    }

    public UUID getId() {
        return id;
    }

    public Cluster getCluster() {
        return cluster;
    }

    public void setCluster(Cluster cluster) {
        this.cluster = cluster;
    }

    public ComponentKind getKind() {
        return kind;
    }

    public ServiceEndpointProtocol getProtocol() {
        return protocol;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getHost() {
        return host;
    }

    public Integer getPort() {
        return port;
    }

    public String getHealthPath() {
        return healthPath;
    }

    public String getVersion() {
        return version;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String describeEndpoint() {
        if (baseUrl != null && !baseUrl.isBlank()) {
            return baseUrl + (healthPath == null ? "" : healthPath);
        }
        if (host != null && port != null) {
            return host + ":" + port;
        }
        return kind.name();
    }
}
