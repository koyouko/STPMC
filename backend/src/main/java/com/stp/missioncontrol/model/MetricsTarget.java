package com.stp.missioncontrol.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "metrics_targets")
public class MetricsTarget {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String host;

    @Column(name = "metrics_port")
    private int metricsPort;

    /** Role label: BROKER, ZOOKEEPER, CONNECT, SCHEMA_REGISTRY, etc. */
    private String role;

    /** Cluster name from CSV upload for grouping in the inventory view */
    @Column(name = "cluster_name")
    private String clusterName;

    /** Environment label from CSV inventory (e.g., DEV, SIT, UAT, PTE, PROD) */
    @Column(length = 20)
    private String environment;

    /** Cluster ID discovered from JMX scrape — populated after scrapeAll() */
    @Column(name = "discovered_cluster_id")
    private String discoveredClusterId;

    private boolean enabled;

    @Column(name = "created_at")
    private Instant createdAt;

    public MetricsTarget() {
    }

    @PrePersist
    public void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (metricsPort == 0) {
            metricsPort = 9404;
        }
        enabled = true;
    }

    public UUID getId() { return id; }

    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }

    public int getMetricsPort() { return metricsPort; }
    public void setMetricsPort(int metricsPort) { this.metricsPort = metricsPort; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getClusterName() { return clusterName; }
    public void setClusterName(String clusterName) { this.clusterName = clusterName; }

    public String getEnvironment() { return environment; }
    public void setEnvironment(String environment) { this.environment = environment; }

    public String getDiscoveredClusterId() { return discoveredClusterId; }
    public void setDiscoveredClusterId(String discoveredClusterId) { this.discoveredClusterId = discoveredClusterId; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public Instant getCreatedAt() { return createdAt; }
}
