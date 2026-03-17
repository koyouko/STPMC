package com.stp.missioncontrol.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        Security security,
        Health health,
        Defaults defaults
) {

    public record Security(
            String mode,
            List<String> allowedOrigins
    ) {
    }

    public record Health(
            long pollIntervalMs,
            long staleAfterMs,
            long refreshCooldownMs,
            int kafkaTimeoutMs,
            int probeTimeoutMs
    ) {
    }

    public record Defaults(
            boolean seedDemoData,
            boolean seedLocalDevCluster,
            String localKafkaBootstrap,
            String localSchemaRegistryUrl
    ) {
    }
}
