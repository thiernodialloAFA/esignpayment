package com.esign.payment.model;

import com.esign.payment.model.enums.SignerStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "document_signers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentSigner {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private SignerStatus status = SignerStatus.PENDING;

    @Column(unique = true)
    private String signatureToken;

    @Column(length = 20)
    private String phone;

    @Column(length = 6)
    private String otpCode;

    private LocalDateTime otpExpiresAt;

    @Builder.Default
    private boolean otpVerified = false;

    @Builder.Default
    private int otpAttempts = 0;

    private LocalDateTime signedAt;

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
