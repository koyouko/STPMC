package com.stp.missioncontrol.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class DevUserAuthenticationFilterTests {

    private final DevUserAuthenticationFilter filter = new DevUserAuthenticationFilter();

    @Test
    void rolesAreHardcodedAndNeverReadFromHeaders() throws Exception {
        SecurityContextHolder.clearContext();

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/platform/clusters");
        // Attacker tries to inject a custom role via header
        request.addHeader("X-MC-Roles", "ROLE_SUPER_ADMIN,ROLE_ROOT");

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilterInternal(request, response, new MockFilterChain());

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();

        Set<String> roles = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());

        // Only the three hardcoded roles should be present
        assertThat(roles).containsExactlyInAnyOrder(
                "ROLE_PLATFORM_ADMIN", "ROLE_OPERATOR", "ROLE_AUDITOR");
        // The injected role must NOT be present
        assertThat(roles).doesNotContain("ROLE_SUPER_ADMIN", "ROLE_ROOT");
    }

    @Test
    void usernameDefaultsWhenHeaderMissing() throws Exception {
        SecurityContextHolder.clearContext();

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/platform/clusters");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilterInternal(request, response, new MockFilterChain());

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth.getPrincipal()).isEqualTo("local-platform-admin");
    }

    @Test
    void usernameCanBeOverriddenViaHeader() throws Exception {
        SecurityContextHolder.clearContext();

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/platform/clusters");
        request.addHeader("X-MC-User", "test-operator");

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilterInternal(request, response, new MockFilterChain());

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth.getPrincipal()).isEqualTo("test-operator");
    }

    @Test
    void nonApiPathsAreNotAuthenticated() throws Exception {
        SecurityContextHolder.clearContext();

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/index.html");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilterInternal(request, response, new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void responseHeaderIndicatesDevMode() throws Exception {
        SecurityContextHolder.clearContext();

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/platform/clusters");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilterInternal(request, response, new MockFilterChain());

        assertThat(response.getHeader("X-MC-Auth-Mode")).isEqualTo("development");
    }
}
