package com.stp.missioncontrol.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Development-only authentication filter. Grants all platform roles automatically.
 * <p>
 * SECURITY: Roles are hardcoded and never read from request headers.
 * The only client-controllable value is the display username (X-MC-User),
 * which has no effect on authorization decisions.
 */
public class DevUserAuthenticationFilter extends OncePerRequestFilter {

    private static final List<SimpleGrantedAuthority> DEV_AUTHORITIES = List.of(
            new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN"),
            new SimpleGrantedAuthority("ROLE_OPERATOR"),
            new SimpleGrantedAuthority("ROLE_AUDITOR")
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // Tag every response so dev mode is visible in browser DevTools
        response.setHeader("X-MC-Auth-Mode", "development");

        if (SecurityContextHolder.getContext().getAuthentication() == null
                && request.getRequestURI().startsWith("/api/")) {

            // Allow the display name to be overridden for audit trail readability
            String username = request.getHeader("X-MC-User");
            if (username == null || username.isBlank()) {
                username = "local-platform-admin";
            }

            // SECURITY: Roles are never sourced from request headers.
            // All dev users get the same fixed set of roles.
            var authentication = new UsernamePasswordAuthenticationToken(
                    username, HttpHeaders.AUTHORIZATION, DEV_AUTHORITIES);
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        filterChain.doFilter(request, response);
    }
}
