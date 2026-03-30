package com.stp.missioncontrol.model;

import com.stp.missioncontrol.model.MissionControlEnums.ClusterEnvironment;
import com.stp.missioncontrol.model.MissionControlEnums.ConnectionMode;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "clusters")
public class Cluster {

    @Id
    private UUID id;

    private String name;

    @Enumerated(EnumType.STRING)
    private ClusterEnvironment environment;

    @Enumerated(EnumType.STRING)
    private ConnectionMode connectionMode;

    private String description;

    @Column(name = "jmx_cluster_id")
    private String jmxClusterId;

    private boolean active;

    private Instant createdAt;

    private Instant updatedAt;

    @OneToMany(mappedBy = "cluster", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("preferred desc, name asc")
    private Set<ClusterListener> listeners = new LinkedHashSet<>();

    @OneToMany(mappedBy = "cluster", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("kind asc")
    private Set<ServiceEndpoint> serviceEndpoints = new LinkedHashSet<>();

    @OneToOne(mappedBy = "cluster", cascade = CascadeType.ALL, orphanRemoval = true)
    private ClusterHealthSnapshot healthSnapshot;

    public Cluster() {
    }

    public Cluster(String name, ClusterEnvironment environment, String description) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.environment = environment;
        this.description = description;
        this.connectionMode = ConnectionMode.DIRECT;
        this.active = true;
    }

    @PrePersist
    public void onCreate() {
        Instant now = Instant.now();
        if (id == null) {
            id = UUID.randomUUID();
        }
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    public void onUpdate() {
        updatedAt = Instant.now();
    }

    public void addListener(ClusterListener listener) {
        listener.setCluster(this);
        listeners.add(listener);
    }

    public void addServiceEndpoint(ServiceEndpoint endpoint) {
        endpoint.setCluster(this);
        serviceEndpoints.add(endpoint);
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public ClusterEnvironment getEnvironment() {
        return environment;
    }

    public ConnectionMode getConnectionMode() {
        return connectionMode;
    }

    public String getDescription() {
        return description;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Set<ClusterListener> getListeners() {
        return listeners;
    }

    public Set<ServiceEndpoint> getServiceEndpoints() {
        return serviceEndpoints;
    }

    public ClusterHealthSnapshot getHealthSnapshot() {
        return healthSnapshot;
    }

    public void setHealthSnapshot(ClusterHealthSnapshot healthSnapshot) {
        if (healthSnapshot != null) {
            healthSnapshot.setCluster(this);
        }
        this.healthSnapshot = healthSnapshot;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getJmxClusterId() {
        return jmxClusterId;
    }

    public void setJmxClusterId(String jmxClusterId) {
        this.jmxClusterId = jmxClusterId;
    }

    public void setEnvironment(ClusterEnvironment environment) {
        this.environment = environment;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public void clearListeners() {
        listeners.clear();
    }

    public void clearServiceEndpoints() {
        serviceEndpoints.clear();
    }
}
