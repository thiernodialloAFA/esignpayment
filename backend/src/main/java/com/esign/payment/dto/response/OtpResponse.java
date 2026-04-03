package com.esign.payment.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OtpResponse {
    private boolean sent;
    private boolean verified;
    private String message;
}
