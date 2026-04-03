package com.esign.payment.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Test helper providing constants and a JWT builder matching the seed user.
 * <p>
 * Security beans (SecurityFilterChain, JwtDecoder, CORS) are supplied by
 * {@link TestProfileSecurityConfig} in src/main (active when profile = "test").
 * This class only provides test constants and the {@link #buildTestJwt()} utility.
 * </p>
 * <p>
 * Use {@code SecurityMockMvcRequestPostProcessors.jwt().jwt(buildTestJwt())} in MockMvc tests
 * to inject the test JWT matching the seed user.
 * </p>
 */
@TestConfiguration
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
}

