package com.stp.missioncontrol.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class DevUserAuthenticationFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null
                && request.getRequestURI().startsWith("/api/")) {
            String username = request.getHeader("X-MC-User");
            if (username == null || username.isBlank()) {
                username = "local-platform-admin";
            }

            String rolesHeader = request.getHeader("X-MC-Roles");
            if (rolesHeader == null || rolesHeader.isBlank()) {
                rolesHeader = "PLATFORM_ADMIN,OPERATOR,AUDITOR";
            }

            var authorities = Arrays.stream(rolesHeader.split(","))
                    .map(String::trim)
                    .filter(role -> !role.isBlank())
                    .map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role)
                    .map(SimpleGrantedAuthority::new)
                    .collect(Collectors.toList());

            var authentication = new UsernamePasswordAuthenticationToken(username, HttpHeaders.AUTHORIZATION, authorities);
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        filterChain.doFilter(request, response);
    }
}
