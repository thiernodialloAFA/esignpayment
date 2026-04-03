package com.esign.payment.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI esignPayOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("ESignPay API")
                        .version("1.0")
                        .description(
                                "API for managing document e-signatures, payments (Stripe 3D Secure), "
                                + "account opening with KYC/OCR verification, and OTP signing via Twilio SMS.")
                        .contact(new Contact()
                                .name("ESignPay Team")))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME, new SecurityScheme()
                                .name(SECURITY_SCHEME_NAME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Keycloak JWT Bearer token — obtain from POST to "
                                        + "http://localhost:9090/realms/esignpayment/protocol/openid-connect/token")));
    }
}

