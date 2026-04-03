package com.esign.payment.dto.request;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class UpdateAccountApplicationRequest {
    private String accountTypeCode;
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
    private BigDecimal monthlyIncome;
}

