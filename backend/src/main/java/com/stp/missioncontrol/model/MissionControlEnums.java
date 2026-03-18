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

    public enum SelfServiceTaskType {
        TOPIC_LIST,
        TOPIC_DESCRIBE,
        TOPIC_CREATE,
        TOPIC_DELETE,
        TOPIC_PURGE,
        TOPIC_INCREASE_PARTITIONS,
        TOPIC_MESSAGE_COUNT,
        TOPIC_CONFIG_DESCRIBE,
        TOPIC_CONFIG_ALTER,
        ACL_LIST,
        ACL_DESCRIBE,
        ACL_GRANT,
        ACL_REMOVE,
        CONSUMER_GROUP_LIST,
        CONSUMER_GROUP_DESCRIBE,
        CONSUMER_GROUP_DELETE,
        CONSUMER_GROUP_OFFSETS,
        TOPIC_DATA_DUMP
    }

    public enum SelfServiceCategory {
        TOPIC,
        ACL,
        CONSUMER_GROUP,
        DATA
    }

    public enum TaskExecutionStatus {
        QUEUED,
        RUNNING,
        SUCCESS,
        FAILED
    }
}
