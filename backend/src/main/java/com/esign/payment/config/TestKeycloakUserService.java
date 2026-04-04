package com.esign.payment.config;

import com.esign.payment.model.User;
import com.esign.payment.repository.UserRepository;
import com.esign.payment.service.KeycloakUserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Test-profile override of {@link KeycloakUserService}.
 * <p>
 * When a JWT is present in the SecurityContext (e.g. MockMvc tests using
 * {@code jwt().jwt(…)}), delegates to {@link #syncUser(Jwt)} so that the
 * user matches the JWT claims — exactly like production.
 * <p>
 * When no JWT is present (e.g. Green Score Analyzer calling without auth),
 * falls back to a deterministic test user ({@code keycloakId = "test-user"})
 * that matches the data seeded by {@link TestDataInitializer}.
 * </p>
 */
@Service
@Primary
@Profile("test")
@Slf4j
public class TestKeycloakUserService extends KeycloakUserService {

    private final UserRepository userRepository;

    public TestKeycloakUserService(UserRepository userRepository) {
        super(userRepository);
        this.userRepository = userRepository;
    }

    // Must match the keycloakId used in TestDataInitializer.seedTestUser()
    private static final String TEST_KEYCLOAK_ID = "test-user";

    @Override
    @Transactional
    public User getCurrentUser() {
        // If a JWT is present (MockMvc tests), use it — just like production
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            return syncUser(jwt);
        }

        // Fallback for test profile without JWT (e.g. Green Score Analyzer)
        return userRepository.findByKeycloakId(TEST_KEYCLOAK_ID)
                .orElseGet(() -> {
                    log.info("[TEST] Auto-creating test user (keycloakId={})", TEST_KEYCLOAK_ID);
                    return userRepository.save(
                            User.builder()
                                    .keycloakId(TEST_KEYCLOAK_ID)
                                    .email("test@esignpay.com")
                                    .firstName("Test")
                                    .lastName("User")
                                    .build()
                    );
                });
    }
}

