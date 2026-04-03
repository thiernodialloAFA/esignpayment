package com.esign.payment.dto.response;

import com.esign.payment.model.enums.KycDocumentStatus;
import com.esign.payment.model.enums.KycDocumentType;
import com.esign.payment.model.enums.OcrVerificationStatus;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data @Builder
public class KycDocumentResponse {
    private UUID id;
    private KycDocumentType documentType;
    private String fileName;
    private KycDocumentStatus status;
    private String rejectionReason;
    private LocalDateTime createdAt;

    // ── OCR Verification ──
    private OcrVerificationStatus ocrStatus;
    private Integer ocrMatchScore;
    private Boolean documentTypeValid;
    private List<OcrVerificationDetail> ocrDetails;
    private List<String> ocrWarnings;
}
