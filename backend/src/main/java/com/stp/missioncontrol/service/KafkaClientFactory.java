package com.stp.missioncontrol.service;

import com.stp.missioncontrol.model.Cluster;
import com.stp.missioncontrol.model.ClusterAuthProfile;
import com.stp.missioncontrol.model.ClusterListener;
import com.stp.missioncontrol.model.MissionControlEnums.AuthProfileType;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.function.Supplier;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.config.SslConfigs;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.springframework.stereotype.Component;

@Component
public class KafkaClientFactory {

    /**
     * Lock protecting the JVM-wide java.security.krb5.conf system property.
     * Multiple clusters may use different Kerberos configs; this ensures
     * the property is set/restored atomically around client creation.
     */
    private static final Object KRB5_LOCK = new Object();

    public ClusterListener resolveListener(Cluster cluster) {
        return cluster.getListeners().stream()
                .filter(ClusterListener::isPreferred)
                .findFirst()
                .orElseGet(() -> cluster.getListeners().stream().findFirst().orElse(null));
    }

    public AdminClient createAdminClient(Cluster cluster, int timeoutMs) {
        ClusterListener listener = resolveListener(cluster);
        if (listener == null) {
            throw new IllegalStateException("Cluster has no configured listener");
        }
        Map<String, Object> config = buildBaseConfig(listener, "mission-control-" + cluster.getId(), timeoutMs);
        return withKrb5Context(listener.getAuthProfile(), () -> AdminClient.create(config));
    }

    public KafkaConsumer<byte[], byte[]> createConsumer(Cluster cluster, String groupId, int timeoutMs) {
        ClusterListener listener = resolveListener(cluster);
        if (listener == null) {
            throw new IllegalStateException("Cluster has no configured listener");
        }
        Map<String, Object> config = buildBaseConfig(listener, "mission-control-consumer-" + cluster.getId(), timeoutMs);
        config.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName());
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName());
        config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        config.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 100);
        return withKrb5Context(listener.getAuthProfile(), () -> new KafkaConsumer<>(config));
    }

    /**
     * Executes a supplier within a synchronized Kerberos context.
     * Sets java.security.krb5.conf before the call and restores the previous
     * value afterward, preventing concurrent cluster operations from
     * overwriting each other's Kerberos configuration.
     */
    public <T> T withKrb5Context(ClusterAuthProfile authProfile, Supplier<T> action) {
        if (authProfile.getType() != AuthProfileType.SASL_GSSAPI) {
            return action.get();
        }
        synchronized (KRB5_LOCK) {
            String previousKrb5 = System.getProperty("java.security.krb5.conf");
            try {
                applyKrb5IfNeeded(authProfile);
                return action.get();
            } finally {
                restoreKrb5(previousKrb5);
            }
        }
    }

    public Map<String, Object> buildBaseConfig(ClusterListener listener, String clientId, int timeoutMs) {
        Map<String, Object> config = new HashMap<>();
        config.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, listener.getBootstrapServer());
        config.put(AdminClientConfig.CLIENT_ID_CONFIG, clientId);
        config.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, timeoutMs);
        config.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, timeoutMs);

        ClusterAuthProfile authProfile = listener.getAuthProfile();
        config.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, authProfile.getSecurityProtocol());
        applyAuthProfile(config, authProfile);
        return config;
    }

    public void applyAuthProfile(Map<String, Object> config, ClusterAuthProfile authProfile) {
        if (authProfile.getType() == AuthProfileType.PLAINTEXT) {
            return;
        }
        if (authProfile.getTruststorePath() != null && !authProfile.getTruststorePath().isBlank()) {
            config.put(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, authProfile.getTruststorePath());
        }
        readSecret(authProfile.getTruststorePasswordFile()).ifPresent(secret ->
                config.put(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG, secret));

        if (authProfile.getType() == AuthProfileType.MTLS_SSL) {
            if (authProfile.getKeystorePath() != null && !authProfile.getKeystorePath().isBlank()) {
                config.put(SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG, authProfile.getKeystorePath());
            }
            readSecret(authProfile.getKeystorePasswordFile()).ifPresent(secret ->
                    config.put(SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG, secret));
            readSecret(authProfile.getKeyPasswordFile()).ifPresent(secret ->
                    config.put(SslConfigs.SSL_KEY_PASSWORD_CONFIG, secret));
        }

        if (authProfile.getType() == AuthProfileType.SASL_GSSAPI) {
            config.put(SaslConfigs.SASL_MECHANISM, "GSSAPI");
            config.put(SaslConfigs.SASL_KERBEROS_SERVICE_NAME,
                    authProfile.getSaslServiceName() == null ? "kafka" : authProfile.getSaslServiceName());
            String jaasConfig = String.format(
                    "com.sun.security.auth.module.Krb5LoginModule required useKeyTab=true storeKey=true keyTab=\"%s\" principal=\"%s\";",
                    authProfile.getKeytabPath(),
                    authProfile.getPrincipal()
            );
            config.put(SaslConfigs.SASL_JAAS_CONFIG, jaasConfig);
        }
    }

    public void applyKrb5IfNeeded(ClusterAuthProfile authProfile) {
        if (authProfile.getType() == AuthProfileType.SASL_GSSAPI
                && authProfile.getKrb5ConfigPath() != null
                && !authProfile.getKrb5ConfigPath().isBlank()) {
            System.setProperty("java.security.krb5.conf", authProfile.getKrb5ConfigPath());
        }
    }

    public void restoreKrb5(String previousKrb5) {
        if (previousKrb5 == null) {
            System.clearProperty("java.security.krb5.conf");
        } else {
            System.setProperty("java.security.krb5.conf", previousKrb5);
        }
    }

    private Optional<String> readSecret(String path) {
        if (path == null || path.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.readString(Path.of(path)).trim());
        } catch (IOException exception) {
            return Optional.empty();
        }
    }
}
