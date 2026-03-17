package com.stp.missioncontrol.model;

import com.stp.missioncontrol.model.MissionControlEnums.ClusterEnvironment;
import com.stp.missioncontrol.model.MissionControlEnums.TokenScope;
import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "service_accounts")
public class ServiceAccount {

    @Id
    private UUID id;

    private String name;

    private String description;

    private boolean active;

    private Instant createdAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "service_account_scopes", joinColumns = @JoinColumn(name = "service_account_id"))
    @Enumerated(EnumType.STRING)
    private Set<TokenScope> scopes = new LinkedHashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "service_account_environments", joinColumns = @JoinColumn(name = "service_account_id"))
    @Enumerated(EnumType.STRING)
    private Set<ClusterEnvironment> allowedEnvironments = new LinkedHashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "service_account_cluster_ids", joinColumns = @JoinColumn(name = "service_account_id"))
    private Set<UUID> allowedClusterIds = new LinkedHashSet<>();

    @OneToMany(mappedBy = "serviceAccount", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt desc")
    private Set<ServiceAccountToken> tokens = new LinkedHashSet<>();

    public ServiceAccount() {
    }

    public ServiceAccount(String name, String description, Set<TokenScope> scopes, Set<ClusterEnvironment> allowedEnvironments, Set<UUID> allowedClusterIds) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.description = description;
        this.active = true;
        this.scopes.addAll(scopes);
        this.allowedEnvironments.addAll(allowedEnvironments);
        this.allowedClusterIds.addAll(allowedClusterIds);
    }

    @PrePersist
    public void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public void addToken(ServiceAccountToken token) {
        token.setServiceAccount(this);
        tokens.add(token);
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
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

    public Set<TokenScope> getScopes() {
        return scopes;
    }

    public Set<ClusterEnvironment> getAllowedEnvironments() {
        return allowedEnvironments;
    }

    public Set<UUID> getAllowedClusterIds() {
        return allowedClusterIds;
    }

    public Set<ServiceAccountToken> getTokens() {
        return tokens;
    }
}
