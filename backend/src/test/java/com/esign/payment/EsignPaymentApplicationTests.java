package com.esign.payment;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.Map;

@SpringBootTest
@ActiveProfiles("test")
@Import(EsignPaymentApplicationTests.TestSecurityConfig.class)
class EsignPaymentApplicationTests {

    @Test
    void contextLoads() {
    }

    @TestConfiguration
    static class TestSecurityConfig {
        @Bean
        public JwtDecoder jwtDecoder() {
            return token -> Jwt.withTokenValue(token)
                    .header("alg", "RS256")
                    .claim("sub", "test-user")
                    .claim("email", "test@example.com")
                    .claim("given_name", "Test")
                    .claim("family_name", "User")
                    .claim("realm_access", Map.of("roles", java.util.List.of("ROLE_USER")))
                    .issuedAt(Instant.now())
                    .expiresAt(Instant.now().plusSeconds(3600))
                    .build();
        }
    }
}
