package com.stp.missioncontrol.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        Security security,
        Health health,
        Metrics metrics,
        Defaults defaults
) {

    public record Security(
            String mode,
            List<String> allowedOrigins,
            String secretsBaseDir
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

    public record Metrics(
            int scrapeTimeoutMs,
            /** Background auto-scrape interval in ms; 0 disables auto-scraping. */
            long scrapeIntervalMs,
            /** Delay before the first auto-scrape after application startup, in ms. */
            long scrapeInitialDelayMs
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
