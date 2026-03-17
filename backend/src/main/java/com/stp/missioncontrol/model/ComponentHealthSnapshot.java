package com.stp.missioncontrol.model;

import com.stp.missioncontrol.model.MissionControlEnums.CheckSource;
import com.stp.missioncontrol.model.MissionControlEnums.ComponentKind;
import com.stp.missioncontrol.model.MissionControlEnums.HealthStatus;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "component_health_snapshots")
public class ComponentHealthSnapshot {

    @Id
    private UUID id;

    @ManyToOne(optional = false)
    private ClusterHealthSnapshot clusterHealthSnapshot;

    @Enumerated(EnumType.STRING)
    private ComponentKind kind;

    @Enumerated(EnumType.STRING)
    private HealthStatus status;

    @Enumerated(EnumType.STRING)
    private CheckSource checkSource;

    private String endpoint;

    private Long latencyMs;

    private String message;

    private String version;

    private Instant lastCheckedAt;

    public ComponentHealthSnapshot() {
    }

    public ComponentHealthSnapshot(ComponentKind kind, HealthStatus status, CheckSource checkSource, String endpoint, Long latencyMs, String message, String version, Instant lastCheckedAt) {
        this.id = UUID.randomUUID();
        this.kind = kind;
        this.status = status;
        this.checkSource = checkSource;
        this.endpoint = endpoint;
        this.latencyMs = latencyMs;
        this.message = message;
        this.version = version;
        this.lastCheckedAt = lastCheckedAt;
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

    public ClusterHealthSnapshot getClusterHealthSnapshot() {
        return clusterHealthSnapshot;
    }

    public void setClusterHealthSnapshot(ClusterHealthSnapshot clusterHealthSnapshot) {
        this.clusterHealthSnapshot = clusterHealthSnapshot;
    }

    public ComponentKind getKind() {
        return kind;
    }

    public HealthStatus getStatus() {
        return status;
    }

    public CheckSource getCheckSource() {
        return checkSource;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public Long getLatencyMs() {
        return latencyMs;
    }

    public String getMessage() {
        return message;
    }

    public String getVersion() {
        return version;
    }

    public Instant getLastCheckedAt() {
        return lastCheckedAt;
    }
}
