package com.esign.payment.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Security configuration for the "test" profile.
 * <p>
 * All endpoints are open (permitAll), no OAuth2/Keycloak required.
 * A stub JwtDecoder is provided so the context starts without a real OIDC provider.
 * </p>
 */
@Configuration
@EnableWebSecurity
@Profile("test")
public class TestProfileSecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .oauth2ResourceServer(AbstractHttpConfigurer::disable);

        return http.build();
    }

    /**
     * Stub JwtDecoder — satisfies Spring auto-config without a real Keycloak.
     */
    @Bean
    public JwtDecoder jwtDecoder() {
        return token -> Jwt.withTokenValue("stub-token")
                .header("alg", "none")
                .claim("sub", "test-user")
                .claim("email", "test@esignpay.com")
                .claim("given_name", "Test")
                .claim("family_name", "User")
                .claim("realm_access", Map.of("roles", List.of("ROLE_USER")))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of("*"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}

