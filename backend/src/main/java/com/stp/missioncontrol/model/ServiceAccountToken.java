package com.stp.missioncontrol.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "service_account_tokens")
public class ServiceAccountToken {

    @Id
    private UUID id;

    @ManyToOne(optional = false)
    private ServiceAccount serviceAccount;

    private String name;

    private String tokenPrefix;

    private String tokenHash;

    private Instant createdAt;

    private Instant expiresAt;

    private Instant lastUsedAt;

    private boolean revoked;

    public ServiceAccountToken() {
    }

    public ServiceAccountToken(String name, String tokenPrefix, String tokenHash, Instant expiresAt) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.tokenPrefix = tokenPrefix;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.revoked = false;
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

    public UUID getId() {
        return id;
    }

    public ServiceAccount getServiceAccount() {
        return serviceAccount;
    }

    public void setServiceAccount(ServiceAccount serviceAccount) {
        this.serviceAccount = serviceAccount;
    }

    public String getName() {
        return name;
    }

    public String getTokenPrefix() {
        return tokenPrefix;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getLastUsedAt() {
        return lastUsedAt;
    }

    public void markUsed() {
        this.lastUsedAt = Instant.now();
    }

    public boolean isRevoked() {
        return revoked;
    }

    public void revoke() {
        this.revoked = true;
    }
}
