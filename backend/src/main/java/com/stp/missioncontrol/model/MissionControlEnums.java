package com.stp.missioncontrol.model;

public final class MissionControlEnums {

    private MissionControlEnums() {
    }

    public enum ClusterEnvironment {
        PROD,
        NON_PROD
    }

    public enum ConnectionMode {
        DIRECT,
        AGENT
    }

    public enum AuthProfileType {
        PLAINTEXT,
        MTLS_SSL,
        SASL_GSSAPI
    }

    public enum HealthStatus {
        HEALTHY,
        DEGRADED,
        DOWN,
        UNKNOWN,
        NOT_APPLICABLE
    }

    public enum ComponentKind {
        KAFKA,
        ZOOKEEPER,
        SCHEMA_REGISTRY,
        CONTROL_CENTER,
        PROMETHEUS,
        KRAFT,
        MDS
    }

    public enum CheckSource {
        PROMETHEUS,
        TCP,
        HTTP,
        KAFKA_CLIENT
    }

    public enum ServiceEndpointProtocol {
        HTTP,
        HTTPS,
        TCP
    }

    public enum TokenScope {
        HEALTH_READ,
        HEALTH_REFRESH,
        CLUSTER_READ
    }

    public enum RefreshOperationStatus {
        QUEUED,
        RUNNING,
        SUCCESS,
        FAILED,
        RATE_LIMITED
    }

    public enum RefreshTriggerType {
        SCHEDULED,
        API
    }

}
