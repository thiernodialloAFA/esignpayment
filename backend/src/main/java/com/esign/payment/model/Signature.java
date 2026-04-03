package com.esign.payment.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "signatures")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Signature {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "signer_id", nullable = false)
    private DocumentSigner signer;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String signatureData;

    private String ipAddress;

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime signedAt = LocalDateTime.now();
}
