package com.stp.missioncontrol.controller;

import com.stp.missioncontrol.dto.ApiDtos;
import com.stp.missioncontrol.model.ClusterAuthProfile;
import com.stp.missioncontrol.model.MissionControlEnums.AuthProfileType;
import jakarta.validation.Valid;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.config.SslConfigs;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/platform/clusters")
public class PlatformClusterAdminController {

    private static final int TEST_TIMEOUT_MS = 5000;

    @PostMapping("/test-connection")
    public ApiDtos.TestConnectionResponse testConnection(@Valid @RequestBody ApiDtos.TestConnectionRequest request) {
        Map<String, Object> config = new HashMap<>();
        config.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, request.bootstrapServers());
        config.put(AdminClientConfig.CLIENT_ID_CONFIG, "mission-control-test-connection");
        config.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, TEST_TIMEOUT_MS);
        config.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, TEST_TIMEOUT_MS);
        config.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, request.authProfile().securityProtocol());

        applyAuthProfile(config, request.authProfile());

        Instant started = Instant.now();
        String previousKrb5 = System.getProperty("java.security.krb5.conf");
        try {
            if (request.authProfile().type() == AuthProfileType.SASL_GSSAPI
                    && request.authProfile().krb5ConfigPath() != null
                    && !request.authProfile().krb5ConfigPath().isBlank()) {
                System.setProperty("java.security.krb5.conf", request.authProfile().krb5ConfigPath());
            }
            try (AdminClient adminClient = AdminClient.create(config)) {
                String clusterId = adminClient.describeCluster().clusterId().get(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                int nodeCount = adminClient.describeCluster().nodes().get(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS).size();
                long latency = Duration.between(started, Instant.now()).toMillis();
                return new ApiDtos.TestConnectionResponse(true, clusterId, nodeCount, latency, null);
            }
        } catch (Exception exception) {
            long latency = Duration.between(started, Instant.now()).toMillis();
            String message = exception.getCause() != null ? exception.getCause().getMessage() : exception.getMessage();
            return new ApiDtos.TestConnectionResponse(false, null, 0, latency, message);
        } finally {
            if (previousKrb5 == null) {
                System.clearProperty("java.security.krb5.conf");
            } else {
                System.setProperty("java.security.krb5.conf", previousKrb5);
            }
        }
    }

    private void applyAuthProfile(Map<String, Object> config, ApiDtos.AuthProfileRequest authProfile) {
        if (authProfile.type() == AuthProfileType.PLAINTEXT) {
            return;
        }
        if (authProfile.truststorePath() != null && !authProfile.truststorePath().isBlank()) {
            config.put(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, authProfile.truststorePath());
        }
        if (authProfile.type() == AuthProfileType.MTLS_SSL) {
            if (authProfile.keystorePath() != null && !authProfile.keystorePath().isBlank()) {
                config.put(SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG, authProfile.keystorePath());
            }
        }
        if (authProfile.type() == AuthProfileType.SASL_GSSAPI) {
            config.put(SaslConfigs.SASL_MECHANISM, "GSSAPI");
            config.put(SaslConfigs.SASL_KERBEROS_SERVICE_NAME, authProfile.saslServiceName() == null ? "kafka" : authProfile.saslServiceName());
            String jaasConfig = String.format(
                    "com.sun.security.auth.module.Krb5LoginModule required useKeyTab=true storeKey=true keyTab=\"%s\" principal=\"%s\";",
                    authProfile.keytabPath(),
                    authProfile.principal()
            );
            config.put(SaslConfigs.SASL_JAAS_CONFIG, jaasConfig);
        }
    }
}
