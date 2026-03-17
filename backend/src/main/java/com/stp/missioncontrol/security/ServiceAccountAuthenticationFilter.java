package com.stp.missioncontrol.security;

import com.stp.missioncontrol.service.ServiceAccountService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class ServiceAccountAuthenticationFilter extends OncePerRequestFilter {

    private final ServiceAccountService serviceAccountService;

    public ServiceAccountAuthenticationFilter(ServiceAccountService serviceAccountService) {
        this.serviceAccountService = serviceAccountService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/external/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Bearer token is required");
            return;
        }

        try {
            var principal = serviceAccountService.authenticate(authorization.substring("Bearer ".length()));
            var authorities = principal.scopes().stream()
                    .map(scope -> new SimpleGrantedAuthority("SCOPE_" + scope.name()))
                    .toList();
            var authentication = new UsernamePasswordAuthenticationToken(principal, authorization, authorities);
            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
        } catch (IllegalArgumentException exception) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, exception.getMessage());
        }
    }
}
