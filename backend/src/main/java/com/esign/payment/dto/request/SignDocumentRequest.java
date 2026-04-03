package com.esign.payment.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SignDocumentRequest {

    @NotBlank(message = "Signature data is required")
    private String signatureData;
}
