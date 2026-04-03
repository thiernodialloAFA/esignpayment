package com.esign.payment.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
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
 * Test security configuration that completely disables OAuth2/Keycloak authentication.
 * <p>
 * - All endpoints are open (permitAll).
 * - A stub JwtDecoder is provided so Spring context starts without a real Keycloak.
 * - Use {@code SecurityMockMvcRequestPostProcessors.jwt().jwt(buildTestJwt())} in MockMvc tests
 *   to inject the test JWT matching the seed user.
 * </p>
 */
@TestConfiguration
@EnableWebSecurity
@EnableMethodSecurity
public class TestSecurityConfig {

    public static final String TEST_KEYCLOAK_ID = "test-user-keycloak-id";
    public static final String TEST_EMAIL = "test@esignpay.com";
    public static final String TEST_FIRST_NAME = "Test";
    public static final String TEST_LAST_NAME = "User";

    /**
     * Builds a realistic Jwt token matching the test user seed data.
     */
    public static Jwt buildTestJwt() {
        return Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .claim("sub", TEST_KEYCLOAK_ID)
                .claim("email", TEST_EMAIL)
                .claim("given_name", TEST_FIRST_NAME)
                .claim("family_name", TEST_LAST_NAME)
                .claim("realm_access", Map.of("roles", List.of("ROLE_USER")))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
    }

    /**
     * Security filter chain that permits ALL requests — no auth required.
     */
    @Bean
    @Primary
    public SecurityFilterChain testFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(testCorsConfigurationSource()))
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .oauth2ResourceServer(AbstractHttpConfigurer::disable);

        return http.build();
    }

    /**
     * Stub JwtDecoder — always returns the test JWT. Required to satisfy Spring auto-config.
     */
    @Bean
    @Primary
    public JwtDecoder jwtDecoder() {
        return token -> buildTestJwt();
    }

    /**
     * Filter removed — use SecurityMockMvcRequestPostProcessors.jwt() in MockMvc tests
     * or SecurityContextHolder for non-MockMvc tests.
     */

    @Bean
    public CorsConfigurationSource testCorsConfigurationSource() {
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

