package com.esign.payment.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OcrVerificationDetail {
    private String fieldName;
    private String fieldLabel;
    private String declaredValue;
    private String extractedValue;
    private int matchScore;
    private boolean matched;
}

