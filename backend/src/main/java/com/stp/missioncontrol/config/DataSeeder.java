package com.stp.missioncontrol.config;

import com.stp.missioncontrol.dto.ApiDtos;
import com.stp.missioncontrol.model.MissionControlEnums.AuthProfileType;
import com.stp.missioncontrol.model.MissionControlEnums.ClusterEnvironment;
import com.stp.missioncontrol.model.MissionControlEnums.ComponentKind;
import com.stp.missioncontrol.model.MissionControlEnums.ServiceEndpointProtocol;
import com.stp.missioncontrol.service.ClusterService;
import com.stp.missioncontrol.service.HealthService;
import com.stp.missioncontrol.service.ServiceAccountService;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static com.stp.missioncontrol.model.MissionControlEnums.TokenScope.CLUSTER_READ;
import static com.stp.missioncontrol.model.MissionControlEnums.TokenScope.HEALTH_READ;
import static com.stp.missioncontrol.model.MissionControlEnums.TokenScope.HEALTH_REFRESH;

@Configuration
public class DataSeeder {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    @Bean
    CommandLineRunner seedDemoData(
            AppProperties properties,
            ClusterService clusterService,
            HealthService healthService,
            ServiceAccountService serviceAccountService,
            @Value("${spring.datasource.url:}") String datasourceUrl
    ) {
        return args -> {
            if (clusterService.hasClusters()) {
                return;
            }

            // Guard: skip demo seeding when a production database is detected
            // unless explicitly opted in via APP_SEED_DEMO_DATA=true
            boolean isProductionDb = datasourceUrl.contains("postgresql")
                    || datasourceUrl.contains("mysql")
                    || datasourceUrl.contains("oracle");
            if (isProductionDb) {
                log.warn("Production database detected ({}). Demo data seeding skipped. "
                        + "Set APP_SEED_DEMO_DATA=true explicitly to override.",
                        datasourceUrl.replaceAll("password=[^&;]*", "password=***"));
                return;
            }

            boolean seededLocalCluster = false;
            if (properties.defaults().seedLocalDevCluster() && isPortReachable(properties.defaults().localKafkaBootstrap())) {
                var localBootstrap = properties.defaults().localKafkaBootstrap();
                var localCluster = clusterService.createCluster(
                        new ApiDtos.CreateClusterRequest(
                                "Local Kafka Dev",
                                ClusterEnvironment.NON_PROD,
                                "Auto-detected localhost Kafka profile for development",
                                List.of(new ApiDtos.ClusterListenerRequest(
                                        "local-plaintext",
                                        parseHost(localBootstrap),
                                        parsePort(localBootstrap),
                                        true,
                                        new ApiDtos.AuthProfileRequest(
                                                "local-plaintext",
                                                AuthProfileType.PLAINTEXT,
                                                "PLAINTEXT",
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null
                                        )
                                )),
                                List.of(new ApiDtos.ServiceEndpointRequest(
                                        ComponentKind.SCHEMA_REGISTRY,
                                        ServiceEndpointProtocol.HTTP,
                                        properties.defaults().localSchemaRegistryUrl(),
                                        null,
                                        null,
                                        "/subjects",
                                        "local"
                                ))
                        ),
                        "bootstrap"
                );
                healthService.refreshClusterImmediately(localCluster.getId(), "bootstrap");
                seededLocalCluster = true;
            }

            if (!seededLocalCluster && properties.defaults().seedDemoData()) {
                clusterService.createCluster(
                        new ApiDtos.CreateClusterRequest(
                                "Confluent Prod East",
                                ClusterEnvironment.PROD,
                                "Primary production cluster with mTLS listener on 9094",
                                List.of(new ApiDtos.ClusterListenerRequest(
                                        "prod-east-mtls",
                                        "prod-kafka-east.internal",
                                        9094,
                                        true,
                                        new ApiDtos.AuthProfileRequest(
                                                "prod-mtls",
                                                AuthProfileType.MTLS_SSL,
                                                "SSL",
                                                "/mnt/secrets/prod/truststore.p12",
                                                "/mnt/secrets/prod/truststore.password",
                                                "/mnt/secrets/prod/keystore.p12",
                                                "/mnt/secrets/prod/keystore.password",
                                                "/mnt/secrets/prod/key.password",
                                                null,
                                                null,
                                                null,
                                                null
                                        )
                                )),
                                List.of(
                                        new ApiDtos.ServiceEndpointRequest(ComponentKind.SCHEMA_REGISTRY, ServiceEndpointProtocol.HTTP, "http://prod-schema-east.internal:8081", null, null, "/health", "7.7.x"),
                                        new ApiDtos.ServiceEndpointRequest(ComponentKind.CONTROL_CENTER, ServiceEndpointProtocol.HTTP, "http://prod-control-center.internal:9021", null, null, "/api/health", "7.7.x"),
                                        new ApiDtos.ServiceEndpointRequest(ComponentKind.PROMETHEUS, ServiceEndpointProtocol.HTTP, "http://prod-prometheus.internal:9090", null, null, "/-/healthy", "2.53.x")
                                )
                        ),
                        "bootstrap"
                );

                clusterService.createCluster(
                        new ApiDtos.CreateClusterRequest(
                                "Confluent UAT West",
                                ClusterEnvironment.NON_PROD,
                                "UAT cluster using Kerberos listener on 9095",
                                List.of(new ApiDtos.ClusterListenerRequest(
                                        "uat-west-kerberos",
                                        "uat-kafka-west.internal",
                                        9095,
                                        true,
                                        new ApiDtos.AuthProfileRequest(
                                                "uat-kerberos",
                                                AuthProfileType.SASL_GSSAPI,
                                                "SASL_SSL",
                                                "/mnt/secrets/uat/truststore.p12",
                                                "/mnt/secrets/uat/truststore.password",
                                                null,
                                                null,
                                                null,
                                                "mission-control/uat@EXAMPLE.COM",
                                                "/mnt/secrets/uat/mission-control.keytab",
                                                "/mnt/secrets/uat/krb5.conf",
                                                "kafka"
                                        )
                                )),
                                List.of(
                                        new ApiDtos.ServiceEndpointRequest(ComponentKind.ZOOKEEPER, ServiceEndpointProtocol.TCP, null, "uat-zookeeper.internal", 2181, null, "3.8.x"),
                                        new ApiDtos.ServiceEndpointRequest(ComponentKind.SCHEMA_REGISTRY, ServiceEndpointProtocol.HTTP, "http://uat-schema-west.internal:8081", null, null, "/health", "7.6.x"),
                                        new ApiDtos.ServiceEndpointRequest(ComponentKind.PROMETHEUS, ServiceEndpointProtocol.HTTP, "http://uat-prometheus.internal:9090", null, null, "/-/healthy", "2.53.x")
                                )
                        ),
                        "bootstrap"
                );
            }

            serviceAccountService.createServiceAccount(
                    new ApiDtos.CreateServiceAccountRequest(
                            "external-observer",
                            "Example machine account for health integrations",
                            Set.of(HEALTH_READ, HEALTH_REFRESH, CLUSTER_READ),
                            Set.of(ClusterEnvironment.PROD, ClusterEnvironment.NON_PROD),
                            Set.of()
                    ),
                    "bootstrap"
            );
        };
    }

    private boolean isPortReachable(String bootstrap) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(parseHost(bootstrap), parsePort(bootstrap)), 1500);
            return true;
        } catch (IOException exception) {
            return false;
        }
    }

    private String parseHost(String bootstrap) {
        return bootstrap.split(":")[0];
    }

    private int parsePort(String bootstrap) {
        return Integer.parseInt(bootstrap.split(":")[1]);
    }
}
