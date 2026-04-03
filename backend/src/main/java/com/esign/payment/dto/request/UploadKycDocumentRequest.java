package com.esign.payment.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UploadKycDocumentRequest {
    @NotBlank private String documentType;
    @NotBlank private String fileName;
    @NotBlank private String contentType;
    @NotBlank private String fileContent;
}

