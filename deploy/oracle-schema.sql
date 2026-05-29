-- Oracle schema for STP Kafka Mission Control.
-- Run as the application schema owner before starting with HIBERNATE_DDL_AUTO=validate.
-- UUID identifiers are stored as RAW(16), booleans as NUMBER(1), and Java Instant values
-- as TIMESTAMP(6) WITH TIME ZONE.

CREATE TABLE clusters (
    id RAW(16) NOT NULL,
    name VARCHAR2(255 CHAR),
    environment VARCHAR2(255 CHAR),
    connection_mode VARCHAR2(255 CHAR),
    description VARCHAR2(255 CHAR),
    jmx_cluster_id VARCHAR2(255 CHAR),
    active NUMBER(1) DEFAULT 1 NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE,
    updated_at TIMESTAMP(6) WITH TIME ZONE,
    CONSTRAINT pk_clusters PRIMARY KEY (id),
    CONSTRAINT chk_clusters_active CHECK (active IN (0, 1))
);

CREATE TABLE cluster_auth_profiles (
    id RAW(16) NOT NULL,
    cluster_id RAW(16) NOT NULL,
    name VARCHAR2(255 CHAR),
    type VARCHAR2(255 CHAR),
    security_protocol VARCHAR2(255 CHAR),
    truststore_path VARCHAR2(255 CHAR),
    truststore_password_file VARCHAR2(255 CHAR),
    keystore_path VARCHAR2(255 CHAR),
    keystore_password_file VARCHAR2(255 CHAR),
    key_password_file VARCHAR2(255 CHAR),
    principal VARCHAR2(255 CHAR),
    keytab_path VARCHAR2(255 CHAR),
    krb5_config_path VARCHAR2(255 CHAR),
    sasl_service_name VARCHAR2(255 CHAR),
    active NUMBER(1) DEFAULT 1 NOT NULL,
    CONSTRAINT pk_cluster_auth_profiles PRIMARY KEY (id),
    CONSTRAINT fk_cap_cluster FOREIGN KEY (cluster_id) REFERENCES clusters (id),
    CONSTRAINT chk_cap_active CHECK (active IN (0, 1))
);

CREATE TABLE cluster_listeners (
    id RAW(16) NOT NULL,
    cluster_id RAW(16) NOT NULL,
    auth_profile_id RAW(16) NOT NULL,
    name VARCHAR2(255 CHAR),
    host VARCHAR2(255 CHAR),
    port NUMBER(10),
    preferred NUMBER(1) DEFAULT 0 NOT NULL,
    CONSTRAINT pk_cluster_listeners PRIMARY KEY (id),
    CONSTRAINT fk_cl_cluster FOREIGN KEY (cluster_id) REFERENCES clusters (id),
    CONSTRAINT fk_cl_auth_profile FOREIGN KEY (auth_profile_id) REFERENCES cluster_auth_profiles (id),
    CONSTRAINT chk_cl_preferred CHECK (preferred IN (0, 1))
);

CREATE TABLE service_endpoints (
    id RAW(16) NOT NULL,
    cluster_id RAW(16) NOT NULL,
    kind VARCHAR2(255 CHAR),
    protocol VARCHAR2(255 CHAR),
    base_url VARCHAR2(255 CHAR),
    host VARCHAR2(255 CHAR),
    port NUMBER(10),
    health_path VARCHAR2(255 CHAR),
    version VARCHAR2(255 CHAR),
    enabled NUMBER(1) DEFAULT 1 NOT NULL,
    CONSTRAINT pk_service_endpoints PRIMARY KEY (id),
    CONSTRAINT fk_se_cluster FOREIGN KEY (cluster_id) REFERENCES clusters (id),
    CONSTRAINT chk_se_enabled CHECK (enabled IN (0, 1))
);

CREATE TABLE cluster_health_snapshots (
    id RAW(16) NOT NULL,
    cluster_id RAW(16) NOT NULL,
    status VARCHAR2(255 CHAR),
    summary_message VARCHAR2(255 CHAR),
    last_checked_at TIMESTAMP(6) WITH TIME ZONE,
    stale_after TIMESTAMP(6) WITH TIME ZONE,
    refresh_duration_ms NUMBER(19),
    CONSTRAINT pk_cluster_health_snapshots PRIMARY KEY (id),
    CONSTRAINT uq_chs_cluster UNIQUE (cluster_id),
    CONSTRAINT fk_chs_cluster FOREIGN KEY (cluster_id) REFERENCES clusters (id)
);

CREATE TABLE component_health_snapshots (
    id RAW(16) NOT NULL,
    cluster_health_snapshot_id RAW(16) NOT NULL,
    kind VARCHAR2(255 CHAR),
    status VARCHAR2(255 CHAR),
    check_source VARCHAR2(255 CHAR),
    endpoint VARCHAR2(255 CHAR),
    latency_ms NUMBER(19),
    message VARCHAR2(255 CHAR),
    version VARCHAR2(255 CHAR),
    last_checked_at TIMESTAMP(6) WITH TIME ZONE,
    CONSTRAINT pk_component_health_snapshots PRIMARY KEY (id),
    CONSTRAINT fk_comp_health_snapshot FOREIGN KEY (cluster_health_snapshot_id)
        REFERENCES cluster_health_snapshots (id)
);

CREATE TABLE service_accounts (
    id RAW(16) NOT NULL,
    name VARCHAR2(255 CHAR),
    description VARCHAR2(255 CHAR),
    active NUMBER(1) DEFAULT 1 NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE,
    CONSTRAINT pk_service_accounts PRIMARY KEY (id),
    CONSTRAINT chk_sa_active CHECK (active IN (0, 1))
);

CREATE TABLE service_account_scopes (
    service_account_id RAW(16) NOT NULL,
    scopes VARCHAR2(255 CHAR),
    CONSTRAINT fk_sas_service_account FOREIGN KEY (service_account_id) REFERENCES service_accounts (id)
);

CREATE TABLE service_account_environments (
    service_account_id RAW(16) NOT NULL,
    allowed_environments VARCHAR2(255 CHAR),
    CONSTRAINT fk_sae_service_account FOREIGN KEY (service_account_id) REFERENCES service_accounts (id)
);

CREATE TABLE service_account_cluster_ids (
    service_account_id RAW(16) NOT NULL,
    allowed_cluster_ids RAW(16),
    CONSTRAINT fk_saci_service_account FOREIGN KEY (service_account_id) REFERENCES service_accounts (id)
);

CREATE TABLE service_account_tokens (
    id RAW(16) NOT NULL,
    service_account_id RAW(16) NOT NULL,
    name VARCHAR2(255 CHAR),
    token_prefix VARCHAR2(255 CHAR),
    token_hash VARCHAR2(255 CHAR),
    created_at TIMESTAMP(6) WITH TIME ZONE,
    expires_at TIMESTAMP(6) WITH TIME ZONE,
    last_used_at TIMESTAMP(6) WITH TIME ZONE,
    revoked NUMBER(1) DEFAULT 0 NOT NULL,
    CONSTRAINT pk_service_account_tokens PRIMARY KEY (id),
    CONSTRAINT fk_sat_service_account FOREIGN KEY (service_account_id) REFERENCES service_accounts (id),
    CONSTRAINT chk_sat_revoked CHECK (revoked IN (0, 1))
);

CREATE TABLE audit_events (
    id RAW(16) NOT NULL,
    actor VARCHAR2(255 CHAR),
    action VARCHAR2(255 CHAR),
    entity_type VARCHAR2(255 CHAR),
    entity_id VARCHAR2(255 CHAR),
    details VARCHAR2(2000 CHAR),
    created_at TIMESTAMP(6) WITH TIME ZONE,
    CONSTRAINT pk_audit_events PRIMARY KEY (id)
);

CREATE TABLE health_refresh_operations (
    id RAW(16) NOT NULL,
    cluster_id RAW(16) NOT NULL,
    status VARCHAR2(255 CHAR),
    trigger_type VARCHAR2(255 CHAR),
    requested_by VARCHAR2(255 CHAR),
    requested_at TIMESTAMP(6) WITH TIME ZONE,
    started_at TIMESTAMP(6) WITH TIME ZONE,
    completed_at TIMESTAMP(6) WITH TIME ZONE,
    message VARCHAR2(255 CHAR),
    CONSTRAINT pk_health_refresh_operations PRIMARY KEY (id),
    CONSTRAINT fk_hro_cluster FOREIGN KEY (cluster_id) REFERENCES clusters (id)
);

CREATE TABLE metrics_targets (
    id RAW(16) NOT NULL,
    host VARCHAR2(255 CHAR) NOT NULL,
    metrics_port NUMBER(10),
    role VARCHAR2(255 CHAR),
    cluster_name VARCHAR2(255 CHAR),
    environment VARCHAR2(20 CHAR),
    discovered_cluster_id VARCHAR2(255 CHAR),
    enabled NUMBER(1) DEFAULT 1 NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE,
    CONSTRAINT pk_metrics_targets PRIMARY KEY (id),
    CONSTRAINT chk_mt_enabled CHECK (enabled IN (0, 1))
);

CREATE INDEX idx_clusters_active ON clusters (active);
CREATE INDEX idx_clusters_environment ON clusters (environment);
CREATE INDEX idx_clusters_jmx_cluster_id ON clusters (jmx_cluster_id);
CREATE INDEX idx_audit_events_created_at ON audit_events (created_at);
CREATE INDEX idx_hro_cluster_requested ON health_refresh_operations (cluster_id, requested_at);
CREATE INDEX idx_metrics_targets_enabled ON metrics_targets (enabled);
