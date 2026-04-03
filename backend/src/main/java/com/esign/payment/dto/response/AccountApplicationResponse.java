package com.esign.payment.dto.response;

import com.esign.payment.model.enums.ApplicationStatus;
import com.esign.payment.model.enums.EmploymentStatus;
import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data @Builder
public class AccountApplicationResponse {
    private UUID id;
    private String referenceNumber;
    private ApplicationStatus status;
    private AccountTypeResponse accountType;
    private LocalDate dateOfBirth;
    private String phoneNumber;
    private String nationality;
    private String addressLine1;
    private String addressLine2;
    private String city;
    private String postalCode;
    private String country;
    private EmploymentStatus employmentStatus;
    private String employerName;
    private String jobTitle;
    private BigDecimal monthlyIncome;
    private UUID contractDocumentId;
    private List<KycDocumentResponse> kycDocuments;
    private List<StatusHistoryResponse> statusHistory;
    private LocalDateTime submittedAt;
    private LocalDateTime approvedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

