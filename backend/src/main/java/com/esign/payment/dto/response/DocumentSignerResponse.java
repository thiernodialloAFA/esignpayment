package com.esign.payment.dto.response;

import com.esign.payment.model.enums.SignerStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class DocumentSignerResponse {
    private UUID id;
    private String email;
    private String name;
    private SignerStatus status;
    private boolean otpVerified;
    private LocalDateTime signedAt;
    private LocalDateTime createdAt;
}
