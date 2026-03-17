package com.stp.missioncontrol.model;

import com.stp.missioncontrol.model.MissionControlEnums.RefreshOperationStatus;
import com.stp.missioncontrol.model.MissionControlEnums.RefreshTriggerType;
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
@Table(name = "health_refresh_operations")
public class HealthRefreshOperation {

    @Id
    private UUID id;

    @ManyToOne(optional = false)
    private Cluster cluster;

    @Enumerated(EnumType.STRING)
    private RefreshOperationStatus status;

    @Enumerated(EnumType.STRING)
    private RefreshTriggerType triggerType;

    private String requestedBy;

    private Instant requestedAt;

    private Instant startedAt;

    private Instant completedAt;

    private String message;

    public HealthRefreshOperation() {
    }

    public HealthRefreshOperation(Cluster cluster, RefreshTriggerType triggerType, String requestedBy) {
        this.id = UUID.randomUUID();
        this.cluster = cluster;
        this.triggerType = triggerType;
        this.requestedBy = requestedBy;
        this.status = RefreshOperationStatus.QUEUED;
    }

    @PrePersist
    public void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (requestedAt == null) {
            requestedAt = Instant.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public Cluster getCluster() {
        return cluster;
    }

    public RefreshOperationStatus getStatus() {
        return status;
    }

    public void setStatus(RefreshOperationStatus status) {
        this.status = status;
    }

    public RefreshTriggerType getTriggerType() {
        return triggerType;
    }

    public String getRequestedBy() {
        return requestedBy;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
