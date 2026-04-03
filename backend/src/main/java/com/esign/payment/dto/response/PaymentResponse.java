package com.esign.payment.dto.response;

import com.esign.payment.model.enums.PaymentStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class PaymentResponse {
    private UUID id;
    private BigDecimal amount;
    private String currency;
    private PaymentStatus status;
    private String description;
    private String providerReference;
    private String clientSecret;
    private String stripePublishableKey;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
