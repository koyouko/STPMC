package com.stp.missioncontrol.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "cluster_listeners")
public class ClusterListener {

    @Id
    private UUID id;

    @ManyToOne(optional = false)
    private Cluster cluster;

    @ManyToOne(fetch = FetchType.EAGER, optional = false, cascade = CascadeType.ALL)
    private ClusterAuthProfile authProfile;

    private String name;

    private String host;

    private int port;

    private boolean preferred;

    public ClusterListener() {
    }

    public ClusterListener(String name, String host, int port, boolean preferred, ClusterAuthProfile authProfile) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.host = host;
        this.port = port;
        this.preferred = preferred;
        this.authProfile = authProfile;
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
        if (authProfile != null) {
            authProfile.setCluster(cluster);
        }
    }

    public ClusterAuthProfile getAuthProfile() {
        return authProfile;
    }

    public String getName() {
        return name;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public boolean isPreferred() {
        return preferred;
    }

    public String getBootstrapServer() {
        return host + ":" + port;
    }
}
