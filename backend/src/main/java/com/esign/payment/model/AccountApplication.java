package com.esign.payment.model;

import com.esign.payment.model.enums.ApplicationStatus;
import com.esign.payment.model.enums.EmploymentStatus;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "account_applications")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AccountApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_type_id", nullable = false)
    private AccountType accountType;

    @Column(nullable = false, unique = true)
    private String referenceNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ApplicationStatus status = ApplicationStatus.DRAFT;

    private LocalDate dateOfBirth;

    @Column(length = 20)
    private String phoneNumber;

    @Column(length = 100)
    private String nationality;

    private String addressLine1;
    private String addressLine2;

    @Column(length = 100)
    private String city;

    @Column(length = 20)
    private String postalCode;

    @Column(length = 100)
    private String country;

    @Enumerated(EnumType.STRING)
    private EmploymentStatus employmentStatus;

    private String employerName;
    private String jobTitle;
    private BigDecimal monthlyIncome;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contract_document_id")
    private Document contractDocument;

    @OneToMany(mappedBy = "application", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<KycDocument> kycDocuments = new ArrayList<>();

    @OneToMany(mappedBy = "application", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ApplicationStatusHistory> statusHistory = new ArrayList<>();

    private LocalDateTime submittedAt;
    private LocalDateTime approvedAt;

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(nullable = false)
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

