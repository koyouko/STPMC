package com.stp.missioncontrol.config;

import com.stp.missioncontrol.security.DevUserAuthenticationFilter;
import com.stp.missioncontrol.security.ServiceAccountAuthenticationFilter;
import com.stp.missioncontrol.service.ServiceAccountService;
import jakarta.annotation.PostConstruct;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    private final AppProperties properties;

    public SecurityConfig(AppProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void logSecurityMode() {
        String mode = properties.security().mode();
        if (!"saml".equalsIgnoreCase(mode)) {
            log.warn("╔══════════════════════════════════════════════════════════════╗");
            log.warn("║  SECURITY MODE: DEVELOPMENT                                 ║");
            log.warn("║  DevUserAuthenticationFilter is active.                      ║");
            log.warn("║  All API requests are auto-authenticated with full roles.    ║");
            log.warn("║  Set APP_SECURITY_MODE=saml for production deployments.      ║");
            log.warn("╚══════════════════════════════════════════════════════════════╝");
        } else {
            log.info("Security mode: SAML — production authentication enabled.");
        }
    }

    @Bean
    @Order(1)
    SecurityFilterChain externalChain(HttpSecurity http, ServiceAccountService serviceAccountService) throws Exception {
        http.securityMatcher("/api/external/**")
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .addFilterBefore(new ServiceAccountAuthenticationFilter(serviceAccountService), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain appChain(HttpSecurity http) throws Exception {
        if ("saml".equalsIgnoreCase(properties.security().mode())) {
            http.csrf(csrf -> csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()));
        } else {
            http.csrf(csrf -> csrf.disable());
        }
        http.cors(Customizer.withDefaults())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/h2-console/**").denyAll()
                        .requestMatchers("/actuator/health", "/error").permitAll()
                        .requestMatchers("/api/platform/self-service/**").hasAnyRole("PLATFORM_ADMIN", "OPERATOR")
                        .requestMatchers(HttpMethod.GET, "/api/platform/**").hasAnyRole("PLATFORM_ADMIN", "OPERATOR", "AUDITOR")
                        .requestMatchers("/api/platform/**").hasAnyRole("PLATFORM_ADMIN", "OPERATOR")
                        .requestMatchers("/api/admin/**").hasRole("PLATFORM_ADMIN")
                        .requestMatchers("/", "/index.html", "/assets/**", "/favicon.ico", "/favicon.svg", "/icons.svg").permitAll()
                        .requestMatchers("/clusters/**", "/metrics/**", "/audit/**").permitAll()
                        .anyRequest().authenticated());

        if ("saml".equalsIgnoreCase(properties.security().mode())) {
            http.saml2Login(Customizer.withDefaults());
        } else {
            http.addFilterBefore(new DevUserAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class);
        }

        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(properties.security().allowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
