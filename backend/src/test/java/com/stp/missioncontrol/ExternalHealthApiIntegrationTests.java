package com.stp.missioncontrol;

import com.stp.missioncontrol.dto.ApiDtos;
import com.stp.missioncontrol.model.MissionControlEnums.AuthProfileType;
import com.stp.missioncontrol.model.MissionControlEnums.ClusterEnvironment;
import com.stp.missioncontrol.model.MissionControlEnums.ComponentKind;
import com.stp.missioncontrol.model.MissionControlEnums.ServiceEndpointProtocol;
import com.stp.missioncontrol.model.MissionControlEnums.TokenScope;
import com.stp.missioncontrol.service.ClusterService;
import com.stp.missioncontrol.service.ServiceAccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.defaults.seed-demo-data=false",
        "app.defaults.seed-local-dev-cluster=false",
        "app.health.poll-interval-ms=600000"
})
@AutoConfigureMockMvc
class ExternalHealthApiIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ClusterService clusterService;

    @Autowired
    private ServiceAccountService serviceAccountService;

    private String token;

    @BeforeEach
    void setUp() {
        if (clusterService.listClusters().isEmpty()) {
            clusterService.createCluster(
                    new ApiDtos.CreateClusterRequest(
                            "test-cluster",
                            ClusterEnvironment.NON_PROD,
                            "Integration test cluster",
                            List.of(new ApiDtos.ClusterListenerRequest(
                                    "listener-1",
                                    "localhost",
                                    9094,
                                    true,
                                    new ApiDtos.AuthProfileRequest(
                                            "mtls",
                                            AuthProfileType.MTLS_SSL,
                                            "SSL",
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
                                    "http://localhost:8081",
                                    null,
                                    null,
                                    "/health",
                                    "7.7.x"
                            ))
                    ),
                    "test"
            );
        }

        var account = serviceAccountService.createServiceAccount(
                new ApiDtos.CreateServiceAccountRequest(
                        "health-reader",
                        "Reads cluster health",
                        Set.of(TokenScope.HEALTH_READ, TokenScope.CLUSTER_READ),
                        Set.of(ClusterEnvironment.PROD, ClusterEnvironment.NON_PROD),
                        Set.of()
                ),
                "test"
        );

        token = serviceAccountService.createToken(
                account.id(),
                new ApiDtos.ServiceAccountTokenRequest("integration-token", null),
                "test"
        ).rawToken();
    }

    @Test
    void externalHealthEndpointReturnsClusterSummaries() throws Exception {
        mockMvc.perform(get("/api/external/v1/clusters/health")
                        .header("Authorization", "Bearer " + token)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].clusterName").value("test-cluster"))
                .andExpect(jsonPath("$[0].status").exists());
    }
}
