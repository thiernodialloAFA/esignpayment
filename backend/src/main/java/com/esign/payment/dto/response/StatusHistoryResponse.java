package com.esign.payment.dto.response;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data @Builder
public class StatusHistoryResponse {
    private String fromStatus;
    private String toStatus;
    private String comment;
    private LocalDateTime changedAt;
}

