package com.stp.missioncontrol.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for security filter chain in dev mode (the default).
 */
@SpringBootTest(properties = {
        "app.security.mode=dev",
        "app.defaults.seed-demo-data=false",
        "app.defaults.seed-local-dev-cluster=false",
        "app.health.poll-interval-ms=600000"
})
@AutoConfigureMockMvc
class SecurityConfigIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void platformEndpointsAreAccessibleInDevMode() throws Exception {
        mockMvc.perform(get("/api/platform/clusters"))
                .andExpect(status().isOk());
    }

    @Test
    void adminEndpointsAreAccessibleInDevMode() throws Exception {
        mockMvc.perform(get("/api/admin/service-accounts"))
                .andExpect(status().isOk());
    }

    @Test
    void devModeResponseHeaderIsSet() throws Exception {
        mockMvc.perform(get("/api/platform/clusters"))
                .andExpect(header().string("X-MC-Auth-Mode", "development"));
    }

    @Test
    void dataDumpReturns400ForMissingClusterNotForbidden() throws Exception {
        // Data dump requires PLATFORM_ADMIN — dev mode grants it, so the request
        // should fail with 400 (cluster not found), NOT 403 (forbidden).
        mockMvc.perform(post("/api/platform/self-service/00000000-0000-0000-0000-000000000001/topics/data-dump")
                        .contentType("application/json")
                        .content("{\"topicName\":\"test\",\"maxMessages\":10}"))
                .andExpect(status().isBadRequest()); // 400, not 403
    }

    @Test
    void externalApiRejectsUnauthenticatedRequests() throws Exception {
        // External API chain requires Bearer token — no token means 401
        mockMvc.perform(get("/api/external/v1/clusters/health"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void healthEndpointIsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }
}
