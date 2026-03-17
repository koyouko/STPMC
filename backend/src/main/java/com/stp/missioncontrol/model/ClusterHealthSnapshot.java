package com.stp.missioncontrol.model;

import com.stp.missioncontrol.model.MissionControlEnums.HealthStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "cluster_health_snapshots")
public class ClusterHealthSnapshot {

    @Id
    private UUID id;

    @OneToOne(optional = false)
    private Cluster cluster;

    @Enumerated(EnumType.STRING)
    private HealthStatus status;

    private String summaryMessage;

    private Instant lastCheckedAt;

    private Instant staleAfter;

    private Long refreshDurationMs;

    @OneToMany(mappedBy = "clusterHealthSnapshot", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("kind asc")
    private Set<ComponentHealthSnapshot> components = new LinkedHashSet<>();

    public ClusterHealthSnapshot() {
    }

    public ClusterHealthSnapshot(Cluster cluster) {
        this.id = UUID.randomUUID();
        this.cluster = cluster;
        this.status = HealthStatus.UNKNOWN;
        this.summaryMessage = "Health has not been collected yet";
    }

    @PrePersist
    public void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
    }

    public void replaceComponents(List<ComponentHealthSnapshot> updatedComponents) {
        components.clear();
        updatedComponents.forEach(component -> {
            component.setClusterHealthSnapshot(this);
            components.add(component);
        });
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

    public HealthStatus getStatus() {
        return status;
    }

    public void setStatus(HealthStatus status) {
        this.status = status;
    }

    public String getSummaryMessage() {
        return summaryMessage;
    }

    public void setSummaryMessage(String summaryMessage) {
        this.summaryMessage = summaryMessage;
    }

    public Instant getLastCheckedAt() {
        return lastCheckedAt;
    }

    public void setLastCheckedAt(Instant lastCheckedAt) {
        this.lastCheckedAt = lastCheckedAt;
    }

    public Instant getStaleAfter() {
        return staleAfter;
    }

    public void setStaleAfter(Instant staleAfter) {
        this.staleAfter = staleAfter;
    }

    public Long getRefreshDurationMs() {
        return refreshDurationMs;
    }

    public void setRefreshDurationMs(Long refreshDurationMs) {
        this.refreshDurationMs = refreshDurationMs;
    }

    public Set<ComponentHealthSnapshot> getComponents() {
        return components;
    }
}
