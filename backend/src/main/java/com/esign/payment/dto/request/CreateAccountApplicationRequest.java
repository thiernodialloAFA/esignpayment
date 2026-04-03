package com.esign.payment.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateAccountApplicationRequest {
    @NotBlank private String accountTypeCode;
    private String dateOfBirth;
    private String phoneNumber;
    private String nationality;
    private String addressLine1;
    private String addressLine2;
    private String city;
    private String postalCode;
    private String country;
    private String employmentStatus;
    private String employerName;
    private String jobTitle;
    private java.math.BigDecimal monthlyIncome;
}

