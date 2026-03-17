package com.stp.missioncontrol.model;

import com.stp.missioncontrol.model.MissionControlEnums.AuthProfileType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "cluster_auth_profiles")
public class ClusterAuthProfile {

    @Id
    private UUID id;

    @ManyToOne(optional = false)
    private Cluster cluster;

    private String name;

    @Enumerated(EnumType.STRING)
    private AuthProfileType type;

    private String securityProtocol;

    private String truststorePath;

    private String truststorePasswordFile;

    private String keystorePath;

    private String keystorePasswordFile;

    private String keyPasswordFile;

    private String principal;

    private String keytabPath;

    private String krb5ConfigPath;

    private String saslServiceName;

    private boolean active;

    public ClusterAuthProfile() {
    }

    public ClusterAuthProfile(String name, AuthProfileType type, String securityProtocol) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.type = type;
        this.securityProtocol = securityProtocol;
        this.active = true;
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

    public String getName() {
        return name;
    }

    public AuthProfileType getType() {
        return type;
    }

    public String getSecurityProtocol() {
        return securityProtocol;
    }

    public String getTruststorePath() {
        return truststorePath;
    }

    public void setTruststorePath(String truststorePath) {
        this.truststorePath = truststorePath;
    }

    public String getTruststorePasswordFile() {
        return truststorePasswordFile;
    }

    public void setTruststorePasswordFile(String truststorePasswordFile) {
        this.truststorePasswordFile = truststorePasswordFile;
    }

    public String getKeystorePath() {
        return keystorePath;
    }

    public void setKeystorePath(String keystorePath) {
        this.keystorePath = keystorePath;
    }

    public String getKeystorePasswordFile() {
        return keystorePasswordFile;
    }

    public void setKeystorePasswordFile(String keystorePasswordFile) {
        this.keystorePasswordFile = keystorePasswordFile;
    }

    public String getKeyPasswordFile() {
        return keyPasswordFile;
    }

    public void setKeyPasswordFile(String keyPasswordFile) {
        this.keyPasswordFile = keyPasswordFile;
    }

    public String getPrincipal() {
        return principal;
    }

    public void setPrincipal(String principal) {
        this.principal = principal;
    }

    public String getKeytabPath() {
        return keytabPath;
    }

    public void setKeytabPath(String keytabPath) {
        this.keytabPath = keytabPath;
    }

    public String getKrb5ConfigPath() {
        return krb5ConfigPath;
    }

    public void setKrb5ConfigPath(String krb5ConfigPath) {
        this.krb5ConfigPath = krb5ConfigPath;
    }

    public String getSaslServiceName() {
        return saslServiceName;
    }

    public void setSaslServiceName(String saslServiceName) {
        this.saslServiceName = saslServiceName;
    }

    public boolean isActive() {
        return active;
    }
}
