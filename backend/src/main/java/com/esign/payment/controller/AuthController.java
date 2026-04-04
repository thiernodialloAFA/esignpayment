package com.esign.payment.controller;

import com.esign.payment.dto.response.ApiResponse;
import com.esign.payment.dto.response.UserResponse;
import com.esign.payment.model.User;
import com.esign.payment.service.KeycloakUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Current user identity (synced from Keycloak)")
public class AuthController {

    private final KeycloakUserService keycloakUserService;

    @GetMapping("/me")
    @Operation(summary = "Get current authenticated user (supports field filtering)")
    public ResponseEntity<ApiResponse<UserResponse>> getCurrentUser(
            @Parameter(description = "Comma-separated list of fields to include")
            @RequestParam(required = false) String fields) {
        User user = keycloakUserService.getCurrentUser();
        UserResponse response = UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .role(user.getRole())
                .createdAt(user.getCreatedAt())
                .build();
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
