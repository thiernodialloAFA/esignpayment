package com.esign.payment.service;

import com.esign.payment.config.ServiceException;
import com.esign.payment.model.User;
import com.esign.payment.model.enums.UserRole;
import com.esign.payment.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class KeycloakUserService {

    private final UserRepository userRepository;

    /**
     * Gets the current authenticated user from the database,
     * creating or updating the user record from Keycloak JWT claims as needed.
     * <p>
     * When running under the "test" profile (no Keycloak), the SecurityContext
     * may contain an anonymous token instead of a JWT.  In that case we fall
     * back to the seeded test user ({@code keycloakId = "test-user"}).
     */
    @Transactional
    public User getCurrentUser() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            return syncUser(jwt);
        }
        // Fallback for test profile (no JWT) — return the seeded test user
        return userRepository.findByKeycloakId("test-user")
                .orElseThrow(() -> new ServiceException(HttpStatus.UNAUTHORIZED,
                        "No authenticated user found. Please log in."));
    }

    /**
     * Syncs a Keycloak user to the local database based on JWT claims.
     * Creates the user if it doesn't exist, or updates if claims have changed.
     */
    @Transactional
    public User syncUser(Jwt jwt) {
        String keycloakId = jwt.getSubject();
        String email = jwt.getClaimAsString("email");
        String firstName = jwt.getClaimAsString("given_name");
        String lastName = jwt.getClaimAsString("family_name");
        UserRole role = extractRole(jwt);

        return userRepository.findByKeycloakId(keycloakId)
                .map(existingUser -> {
                    boolean updated = false;
                    if (email != null && !email.equals(existingUser.getEmail())) {
                        existingUser.setEmail(email);
                        updated = true;
                    }
                    if (firstName != null && !firstName.equals(existingUser.getFirstName())) {
                        existingUser.setFirstName(firstName);
                        updated = true;
                    }
                    if (lastName != null && !lastName.equals(existingUser.getLastName())) {
                        existingUser.setLastName(lastName);
                        updated = true;
                    }
                    if (role != existingUser.getRole()) {
                        existingUser.setRole(role);
                        updated = true;
                    }
                    if (updated) {
                        return userRepository.save(existingUser);
                    }
                    return existingUser;
                })
                .orElseGet(() -> {
                    User newUser = User.builder()
                            .keycloakId(keycloakId)
                            .email(email != null ? email : keycloakId + "@keycloak")
                            .firstName(firstName != null ? firstName : "")
                            .lastName(lastName != null ? lastName : "")
                            .role(role)
                            .build();
                    return userRepository.save(newUser);
                });
    }

    @SuppressWarnings("unchecked")
    private UserRole extractRole(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess != null && realmAccess.containsKey("roles")) {
            List<String> roles = (List<String>) realmAccess.get("roles");
            if (roles.contains("ROLE_ADMIN") || roles.contains("admin")) {
                return UserRole.ROLE_ADMIN;
            }
        }
        return UserRole.ROLE_USER;
    }
}
