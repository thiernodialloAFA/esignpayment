package com.esign.payment.dto.response;

import com.esign.payment.model.enums.DocumentStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class DocumentResponse {
    private UUID id;
    private String title;
    private String description;
    private String fileName;
    private String contentType;
    private DocumentStatus status;
    private UserResponse owner;
    private List<DocumentSignerResponse> signers;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
