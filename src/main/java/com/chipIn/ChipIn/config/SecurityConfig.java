package com.chipIn.ChipIn.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@Slf4j
public class SecurityConfig {

    @Autowired
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Value("${chipin.security.enabled:true}")
    private boolean securityEnabled;

    /**
     * Comma-separated allow-list. Empty by default — production deploys must
     * provide explicit origins. Wildcards are not accepted to avoid the common
     * "{@code *} + credentials" foot-gun.
     */
    @Value("${chipin.cors.allowed-origins:}")
    private String allowedOriginsCsv;

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        if (!securityEnabled) {
            // DEV MODE: Open everything, no filter
            http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        } else {
            // PROD MODE: Secure everything
            http.authorizeHttpRequests(auth -> auth
                            .requestMatchers("/auth/**").permitAll()
                            .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll() // Whitelist Swagger
                            .requestMatchers("/actuator/health", "/actuator/info", "/actuator/prometheus").permitAll()
                            .requestMatchers("/api/users/friends").authenticated() // Explicitly allow the friends endpoint
                            .requestMatchers("/api/**").authenticated()
                            .anyRequest().denyAll()
                    )
                    .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        }

        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        List<String> origins = parseOrigins(allowedOriginsCsv);
        if (origins.isEmpty()) {
            log.warn("CORS allow-list is empty. Set `chipin.cors.allowed-origins` (or CHIPIN_CORS_ALLOWED_ORIGINS) " +
                    "to a comma-separated list of trusted origins. Cross-origin requests will be rejected until then.");
        } else if (origins.contains("*")) {
            // Wildcard with allowCredentials is forbidden by browsers anyway; we
            // refuse it here to surface the misconfiguration earlier.
            throw new IllegalStateException(
                    "chipin.cors.allowed-origins must not contain '*'. List explicit origins instead.");
        } else {
            configuration.setAllowedOrigins(origins);
            configuration.setAllowCredentials(true);
        }

        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList(
                "Authorization", "Content-Type", "Cache-Control", "Idempotency-Key", "X-Request-Id"));
        configuration.setExposedHeaders(List.of("X-Request-Id"));
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    private static List<String> parseOrigins(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
