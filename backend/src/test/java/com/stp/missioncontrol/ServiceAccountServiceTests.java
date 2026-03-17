package com.stp.missioncontrol;

import com.stp.missioncontrol.dto.ApiDtos;
import com.stp.missioncontrol.model.MissionControlEnums.ClusterEnvironment;
import com.stp.missioncontrol.model.MissionControlEnums.TokenScope;
import com.stp.missioncontrol.service.ServiceAccountService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "app.defaults.seed-demo-data=false",
        "app.defaults.seed-local-dev-cluster=false",
        "app.health.poll-interval-ms=600000"
})
class ServiceAccountServiceTests {

    @Autowired
    private ServiceAccountService serviceAccountService;

    @Test
    void issuedTokensAuthenticateAndExposeScopes() {
        var account = serviceAccountService.createServiceAccount(
                new ApiDtos.CreateServiceAccountRequest(
                        "svc-observer",
                        "Token smoke test",
                        Set.of(TokenScope.HEALTH_READ, TokenScope.HEALTH_REFRESH),
                        Set.of(ClusterEnvironment.NON_PROD),
                        Set.of()
                ),
                "test"
        );

        var token = serviceAccountService.createToken(
                account.id(),
                new ApiDtos.ServiceAccountTokenRequest("smoke-token", null),
                "test"
        );

        var principal = serviceAccountService.authenticate(token.rawToken());

        assertThat(principal.name()).isEqualTo("svc-observer");
        assertThat(principal.hasScope(TokenScope.HEALTH_READ)).isTrue();
        assertThat(principal.allowedEnvironments()).containsExactly(ClusterEnvironment.NON_PROD);
    }
}
