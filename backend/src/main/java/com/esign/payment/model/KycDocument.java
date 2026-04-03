package com.esign.payment.model;

import com.esign.payment.model.enums.KycDocumentStatus;
import com.esign.payment.model.enums.KycDocumentType;
import com.esign.payment.model.enums.OcrVerificationStatus;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "kyc_documents")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class KycDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id", nullable = false)
    private AccountApplication application;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private KycDocumentType documentType;

    @Column(nullable = false)
    private String fileName;

    @Column(nullable = false)
    private String filePath;

    @Column(nullable = false)
    private String contentType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private KycDocumentStatus status = KycDocumentStatus.PENDING;

    private String rejectionReason;

    // ── OCR Verification Fields ──

    @Column(columnDefinition = "TEXT")
    private String extractedText;

    @Enumerated(EnumType.STRING)
    @Column(name = "ocr_status")
    @Builder.Default
    private OcrVerificationStatus ocrStatus = OcrVerificationStatus.PENDING;

    @Column(name = "ocr_match_score")
    private Integer ocrMatchScore;

    @Column(name = "ocr_details", columnDefinition = "TEXT")
    private String ocrDetails; // JSON string of OcrVerificationDetail list

    @Column(name = "document_type_valid")
    @Builder.Default
    private Boolean documentTypeValid = false;

    @Column(name = "ocr_warnings", columnDefinition = "TEXT")
    private String ocrWarnings; // JSON string array

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
