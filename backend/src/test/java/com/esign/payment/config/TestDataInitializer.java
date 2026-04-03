package com.esign.payment.config;

import com.esign.payment.model.*;
import com.esign.payment.model.enums.*;
import com.esign.payment.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Seeds the H2 test database with realistic baseline data for integration tests.
 * <p>
 * Creates:
 * - 1 User (matching the test JWT from TestSecurityConfig)
 * - 3 AccountTypes (CHECKING, SAVINGS, PREMIUM)
 * - 2 Documents (DRAFT + PENDING_SIGNATURE with signers)
 * - 2 Payments (PENDING + SUCCEEDED)
 * - 1 AccountApplication (DRAFT) with 1 KycDocument
 * </p>
 */
@TestConfiguration
@RequiredArgsConstructor
@Slf4j
public class TestDataInitializer {

    @Bean
    public CommandLineRunner seedTestData(
            UserRepository userRepository,
            AccountTypeRepository accountTypeRepository,
            DocumentRepository documentRepository,
            DocumentSignerRepository documentSignerRepository,
            PaymentRepository paymentRepository,
            AccountApplicationRepository accountApplicationRepository,
            KycDocumentRepository kycDocumentRepository) {

        return args -> {
            log.info("═══ Seeding test database ═══");

            // ── 1. User (matches TestSecurityConfig JWT) ──
            User testUser = userRepository.save(User.builder()
                    .keycloakId(TestSecurityConfig.TEST_KEYCLOAK_ID)
                    .email(TestSecurityConfig.TEST_EMAIL)
                    .firstName(TestSecurityConfig.TEST_FIRST_NAME)
                    .lastName(TestSecurityConfig.TEST_LAST_NAME)
                    .role(UserRole.ROLE_USER)
                    .build());
            log.info("  ✓ User created: {} ({})", testUser.getEmail(), testUser.getId());

            // ── 2. Account Types ──
            AccountType checking = accountTypeRepository.save(AccountType.builder()
                    .code("CHECKING").label("Compte Courant")
                    .description("Compte courant avec carte bancaire Visa")
                    .monthlyFee(BigDecimal.valueOf(2.00)).isActive(true).build());

            AccountType savings = accountTypeRepository.save(AccountType.builder()
                    .code("SAVINGS").label("Livret Épargne")
                    .description("Livret d'épargne avec taux préférentiel")
                    .monthlyFee(BigDecimal.ZERO).isActive(true).build());

            AccountType premium = accountTypeRepository.save(AccountType.builder()
                    .code("PREMIUM").label("Compte Premium")
                    .description("Compte premium avec carte Gold et assurances incluses")
                    .monthlyFee(BigDecimal.valueOf(12.90)).isActive(true).build());

            log.info("  ✓ Account types created: CHECKING, SAVINGS, PREMIUM");

            // ── 3. Documents ──

            // 3a. Draft document (no signers yet)
            Document draftDoc = documentRepository.save(Document.builder()
                    .title("Contrat de test - Brouillon")
                    .description("Un document de test en statut brouillon")
                    .fileName("test-draft.pdf")
                    .contentType("application/pdf")
                    .filePath("uploads/documents/test-draft.pdf")
                    .status(DocumentStatus.DRAFT)
                    .owner(testUser)
                    .build());
            log.info("  ✓ Document DRAFT created: {}", draftDoc.getId());

            // 3b. Pending signature document with 2 signers
            Document pendingDoc = documentRepository.save(Document.builder()
                    .title("Contrat de test - En attente de signature")
                    .description("Un document envoyé pour signature")
                    .fileName("test-pending.pdf")
                    .contentType("application/pdf")
                    .filePath("uploads/documents/test-pending.pdf")
                    .status(DocumentStatus.PENDING_SIGNATURE)
                    .owner(testUser)
                    .build());

            DocumentSigner signer1 = documentSignerRepository.save(DocumentSigner.builder()
                    .document(pendingDoc)
                    .email("signer1@example.com")
                    .name("Jean Dupont")
                    .phone("+33612345678")
                    .status(SignerStatus.PENDING)
                    .signatureToken("test-token-signer-1")
                    .build());

            DocumentSigner signer2 = documentSignerRepository.save(DocumentSigner.builder()
                    .document(pendingDoc)
                    .email("signer2@example.com")
                    .name("Marie Martin")
                    .phone("+33698765432")
                    .status(SignerStatus.PENDING)
                    .signatureToken("test-token-signer-2")
                    .build());

            log.info("  ✓ Document PENDING_SIGNATURE created: {} with 2 signers", pendingDoc.getId());

            // ── 4. Payments ──
            Payment pendingPayment = paymentRepository.save(Payment.builder()
                    .user(testUser)
                    .amount(BigDecimal.valueOf(49.99))
                    .currency("EUR")
                    .description("Paiement de test - En attente")
                    .status(PaymentStatus.PENDING)
                    .stripePaymentIntentId("pi_test_pending_" + UUID.randomUUID().toString().substring(0, 8))
                    .providerReference("pi_test_pending")
                    .build());

            Payment succeededPayment = paymentRepository.save(Payment.builder()
                    .user(testUser)
                    .amount(BigDecimal.valueOf(150.00))
                    .currency("EUR")
                    .description("Paiement de test - Réussi")
                    .status(PaymentStatus.SUCCEEDED)
                    .stripePaymentIntentId("pi_test_succeeded_" + UUID.randomUUID().toString().substring(0, 8))
                    .providerReference("pi_test_succeeded")
                    .build());

            log.info("  ✓ Payments created: PENDING ({}), SUCCEEDED ({})",
                    pendingPayment.getId(), succeededPayment.getId());

            // ── 5. Account Application ──
            AccountApplication application = accountApplicationRepository.save(AccountApplication.builder()
                    .user(testUser)
                    .accountType(checking)
                    .referenceNumber("ACC-20260403-TEST")
                    .status(ApplicationStatus.DRAFT)
                    .dateOfBirth(LocalDate.of(1990, 5, 15))
                    .phoneNumber("+33612345678")
                    .nationality("Française")
                    .addressLine1("42 rue de la Paix")
                    .addressLine2("Apt 3B")
                    .city("Paris")
                    .postalCode("75002")
                    .country("France")
                    .employmentStatus(EmploymentStatus.EMPLOYED)
                    .employerName("TechCorp")
                    .jobTitle("Développeur Senior")
                    .monthlyIncome(BigDecimal.valueOf(4500.00))
                    .build());

            // KYC document (ID_CARD)
            KycDocument kycDoc = kycDocumentRepository.save(KycDocument.builder()
                    .application(application)
                    .documentType(KycDocumentType.ID_CARD)
                    .fileName("carte-identite.jpg")
                    .filePath("uploads/kyc/test-id-card.jpg")
                    .contentType("image/jpeg")
                    .status(KycDocumentStatus.PENDING)
                    .ocrStatus(OcrVerificationStatus.PENDING)
                    .build());

            log.info("  ✓ AccountApplication DRAFT created: {} with 1 KYC doc", application.getId());

            log.info("═══ Test database seeded successfully ═══");
            log.info("  Summary: 1 user, 3 account types, 2 documents, 2 payments, 1 application");
        };
    }
}

