package com.esign.payment.dto.response;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.util.UUID;

@Data @Builder
public class AccountTypeResponse {
    private UUID id;
    private String code;
    private String label;
    private String description;
    private BigDecimal monthlyFee;
}

