package com.esign.payment.config;

import com.esign.payment.model.User;
import com.esign.payment.repository.UserRepository;
import com.esign.payment.service.KeycloakUserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Test-profile override of {@link KeycloakUserService}.
 * <p>
 * Returns a deterministic test user without requiring a JWT in the SecurityContext.
 * The user is auto-created on first call (keycloakId = "test-user").
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

    @Override
    @Transactional
    public User getCurrentUser() {
        return userRepository.findByKeycloakId("test-user")
                .orElseGet(() -> {
                    log.info("[TEST] Auto-creating test user (keycloakId=test-user)");
                    return userRepository.save(
                            User.builder()
                                    .keycloakId("test-user")
                                    .email("test@esignpay.com")
                                    .firstName("Test")
                                    .lastName("User")
                                    .build()
                    );
                });
    }
}

