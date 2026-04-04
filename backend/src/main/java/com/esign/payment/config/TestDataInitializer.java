package com.esign.payment.config;

import com.esign.payment.model.*;
import com.esign.payment.model.enums.*;
import com.esign.payment.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Seeds all test data on startup when the "test" profile is active.
 * <p>
 * Creates a complete dataset so that every API endpoint discovered by the
 * Green Score Analyzer returns HTTP 200.  Each mutating endpoint gets its
 * own dedicated entity so execution order does not matter.
 * <p>
 * Entity IDs are stored in public static fields and read by
 * {@code GreenScoreTestController}.
 */
@Component
@Profile("test")
@RequiredArgsConstructor
@Slf4j
public class TestDataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final AccountTypeRepository accountTypeRepository;
    private final AccountApplicationRepository applicationRepository;
    private final KycDocumentRepository kycDocumentRepository;
    private final ApplicationStatusHistoryRepository statusHistoryRepository;
    private final DocumentRepository documentRepository;
    private final DocumentSignerRepository documentSignerRepository;
    private final PaymentRepository paymentRepository;

    // ── Public IDs consumed by GreenScoreTestController ──

    public static String TEST_USER_ID;

    // Account applications — each endpoint gets its own app
    public static String APP_DRAFT_ID;          // GET/{id}, PUT/{id}
    public static String APP_SUBMITTABLE_ID;    // POST submit
    public static String APP_KYC_VERIFIED_ID;   // POST generate-contract
    public static String APP_CONTRACT_PENDING_ID; // POST regenerate-contract
    public static String APP_FOR_DELETE_ID;     // DELETE
    public static String APP_FOR_KYC_UPLOAD_ID; // POST kyc

    // KYC
    public static String KYC_DELETABLE_ID;
    public static String KYC_DELETABLE_APP_ID;

    // Documents — each mutating endpoint gets its own doc
    public static String DOC_READ_ID;           // GET/{id}, GET/{id}/download
    public static String DOC_DRAFT_ID;          // POST /send
    public static String DOC_PENDING_ID;        // POST /resend
    public static String DOC_FOR_LIVE_SIGN_ID;  // POST /live-sign
    public static String DOC_FOR_DELETE_ID;     // DELETE

    // Signers
    public static String SIGNER_FOR_LIVE_SIGN_ID;

    // Sign tokens — on a SEPARATE doc that resend won't touch
    public static String TOKEN_VERIFY;
    public static String TOKEN_SEND_OTP;
    public static String TOKEN_VERIFY_OTP;
    public static String TOKEN_SIGN;

    // Payments — separate for confirm and cancel
    public static String PAYMENT_READ_ID;       // GET/{id}
    public static String PAYMENT_CONFIRM_INTENT_ID; // POST confirm
    public static String PAYMENT_CANCEL_ID;     // POST cancel

    // Account type code
    public static String ACCOUNT_TYPE_CODE = "CHECKING";

    @Override
    public void run(String... args) throws Exception {
        log.info("╔══════════════════════════════════════════════════════════╗");
        log.info("║  🧪  Test Data Initializer — seeding green-score data  ║");
        log.info("╚══════════════════════════════════════════════════════════╝");

        createUploadDirectories();

        User testUser = seedTestUser();
        TEST_USER_ID = testUser.getId().toString();

        seedAccountTypes();
        AccountType checking = accountTypeRepository.findByCode("CHECKING").orElseThrow();

        seedApplications(testUser, checking);
        seedDocuments(testUser);
        seedPayments(testUser);

        log.info("✅  Test data seeding completed — all entities independent");
    }

    // ──────────────────────────────────────────────────────────────────────

    private void createUploadDirectories() throws IOException {
        for (String dir : new String[]{"uploads/documents", "uploads/kyc", "uploads/contracts"}) {
            Files.createDirectories(Paths.get(dir));
        }
        log.info("  📁  Upload directories created");
    }

    private User seedTestUser() {
        User user = userRepository.save(
                User.builder()
                        .keycloakId("test-user")
                        .email("test@esignpay.com")
                        .firstName("Test")
                        .lastName("User")
                        .role(UserRole.ROLE_USER)
                        .build()
        );
        log.info("  👤  Test user created: {}", user.getId());
        return user;
    }

    private void seedAccountTypes() {
        if (accountTypeRepository.count() == 0) {
            accountTypeRepository.save(AccountType.builder()
                    .code("CHECKING").label("Compte Courant")
                    .description("Compte courant pour les opérations quotidiennes")
                    .monthlyFee(BigDecimal.ZERO).isActive(true).build());
            accountTypeRepository.save(AccountType.builder()
                    .code("SAVINGS").label("Compte Épargne")
                    .description("Compte épargne avec taux d'intérêt avantageux")
                    .monthlyFee(BigDecimal.ZERO).isActive(true).build());
            accountTypeRepository.save(AccountType.builder()
                    .code("PREMIUM").label("Compte Premium")
                    .description("Compte premium avec services exclusifs et carte Gold")
                    .monthlyFee(new BigDecimal("9.90")).isActive(true).build());
            log.info("  🏦  Account types seeded (CHECKING, SAVINGS, PREMIUM)");
        }
    }

    // ── Applications — one per mutating endpoint ────────────────────────

    private void seedApplications(User user, AccountType accountType) throws IOException {
        // 1. DRAFT — for GET/{id}, PUT/{id} (read-only / safe update)
        AccountApplication appDraft = saveApp(user, accountType, "ACC-TEST-0001", ApplicationStatus.DRAFT);
        APP_DRAFT_ID = appDraft.getId().toString();
        addHistory(appDraft, ApplicationStatus.DRAFT, "Seeded DRAFT", user);

        // 2. DRAFT for KYC upload — separate from the one used for GET
        AccountApplication appForKyc = saveApp(user, accountType, "ACC-TEST-0006", ApplicationStatus.DRAFT);
        APP_FOR_KYC_UPLOAD_ID = appForKyc.getId().toString();

        // 3. DRAFT with KYC deletable doc
        AccountApplication appWithKyc = saveApp(user, accountType, "ACC-TEST-0007", ApplicationStatus.DRAFT);
        KYC_DELETABLE_APP_ID = appWithKyc.getId().toString();
        KycDocument kycDeletable = createKycDocument(appWithKyc, KycDocumentType.PROOF_OF_ADDRESS, "proof.pdf");
        KYC_DELETABLE_ID = kycDeletable.getId().toString();

        // 4. DRAFT fully qualified for submit (all fields + required KYC docs)
        AccountApplication appSubmittable = saveApp(user, accountType, "ACC-TEST-0002", ApplicationStatus.DRAFT);
        APP_SUBMITTABLE_ID = appSubmittable.getId().toString();
        addHistory(appSubmittable, ApplicationStatus.DRAFT, "Seeded for submit", user);
        createApprovedKyc(appSubmittable, KycDocumentType.ID_CARD, "id_card.jpg");
        createApprovedKyc(appSubmittable, KycDocumentType.PROOF_OF_ADDRESS, "proof_address.pdf");

        // 5. KYC_VERIFIED — for generate-contract
        AccountApplication appKycVerified = saveApp(user, accountType, "ACC-TEST-0003", ApplicationStatus.KYC_VERIFIED);
        APP_KYC_VERIFIED_ID = appKycVerified.getId().toString();
        addHistory(appKycVerified, ApplicationStatus.KYC_VERIFIED, "Seeded KYC verified", user);

        // 6. CONTRACT_PENDING — for regenerate-contract
        AccountApplication appContractPending = saveApp(user, accountType, "ACC-TEST-0004", ApplicationStatus.CONTRACT_PENDING);
        Document contractDoc = createTestDocument(user, "Contrat ACC-TEST-0004",
                "contrat_ACC-TEST-0004.pdf", DocumentStatus.PENDING_SIGNATURE);
        documentSignerRepository.save(
                DocumentSigner.builder()
                        .document(contractDoc).email(user.getEmail())
                        .name(user.getFirstName() + " " + user.getLastName())
                        .phone("+33612345678").status(SignerStatus.PENDING)
                        .signatureToken(UUID.randomUUID().toString())
                        .build());
        appContractPending.setContractDocument(contractDoc);
        appContractPending = applicationRepository.save(appContractPending);
        APP_CONTRACT_PENDING_ID = appContractPending.getId().toString();
        addHistory(appContractPending, ApplicationStatus.CONTRACT_PENDING, "Seeded contract pending", user);

        // 7. DRAFT — dedicated for DELETE (expendable)
        AccountApplication appForDelete = saveApp(user, accountType, "ACC-TEST-0005", ApplicationStatus.DRAFT);
        APP_FOR_DELETE_ID = appForDelete.getId().toString();

        log.info("  📋  Account applications seeded (7 apps at various statuses)");
    }

    // ── Documents — isolated per mutating endpoint ─────────────────────

    private void seedDocuments(User user) throws IOException {

        // 1. READ-ONLY document — for GET /{id}, GET /{id}/download (never mutated)
        Document docRead = createTestDocument(user, "Test Document Read",
                "test_read.pdf", DocumentStatus.PENDING_SIGNATURE);
        documentSignerRepository.save(
                DocumentSigner.builder()
                        .document(docRead).email("read-signer@test.com")
                        .name("Read Signer").phone("+33600000000")
                        .status(SignerStatus.PENDING)
                        .signatureToken(UUID.randomUUID().toString())
                        .build());
        DOC_READ_ID = docRead.getId().toString();

        // 2. DRAFT document with signer — dedicated for POST /send
        Document docDraft = createTestDocument(user, "Test Document Draft",
                "test_draft.pdf", DocumentStatus.DRAFT);
        documentSignerRepository.save(
                DocumentSigner.builder()
                        .document(docDraft).email("signer-draft@test.com")
                        .name("Draft Signer").phone("+33600000001")
                        .status(SignerStatus.PENDING)
                        .signatureToken(UUID.randomUUID().toString())
                        .build());
        DOC_DRAFT_ID = docDraft.getId().toString();

        // 3. PENDING_SIGNATURE document — dedicated for POST /resend
        Document docPending = createTestDocument(user, "Test Document Resend",
                "test_resend.pdf", DocumentStatus.PENDING_SIGNATURE);
        documentSignerRepository.save(
                DocumentSigner.builder()
                        .document(docPending).email("resend-signer@test.com")
                        .name("Resend Signer").phone("+33600000010")
                        .status(SignerStatus.PENDING)
                        .signatureToken(UUID.randomUUID().toString())
                        .build());
        DOC_PENDING_ID = docPending.getId().toString();

        // 4. PENDING_SIGNATURE document — dedicated for POST /live-sign
        Document docLiveSign = createTestDocument(user, "Test Document LiveSign",
                "test_livesign.pdf", DocumentStatus.PENDING_SIGNATURE);
        DocumentSigner signerLiveSign = documentSignerRepository.save(
                DocumentSigner.builder()
                        .document(docLiveSign).email("live-signer@test.com")
                        .name("Live Signer").phone("+33600000002")
                        .status(SignerStatus.PENDING)
                        .signatureToken(UUID.randomUUID().toString())
                        .build());
        DOC_FOR_LIVE_SIGN_ID = docLiveSign.getId().toString();
        SIGNER_FOR_LIVE_SIGN_ID = signerLiveSign.getId().toString();

        // 5. SEPARATE document for sign/* endpoints — tokens will NOT be
        //    regenerated by resend because this doc is independent
        Document docSign = createTestDocument(user, "Test Document Sign Tokens",
                "test_sign.pdf", DocumentStatus.PENDING_SIGNATURE);

        // Signer for GET /sign/verify/{token}
        documentSignerRepository.save(
                DocumentSigner.builder()
                        .document(docSign).email("verify@test.com")
                        .name("Verify Signer").phone("+33600000003")
                        .status(SignerStatus.PENDING)
                        .signatureToken("test-token-verify")
                        .build());
        TOKEN_VERIFY = "test-token-verify";

        // Signer for POST /sign/{token}/send-otp
        documentSignerRepository.save(
                DocumentSigner.builder()
                        .document(docSign).email("otp@test.com")
                        .name("OTP Signer").phone("+33600000004")
                        .status(SignerStatus.PENDING)
                        .signatureToken("test-token-otp")
                        .build());
        TOKEN_SEND_OTP = "test-token-otp";

        // Signer for POST /sign/{token}/verify-otp (pre-set OTP code)
        documentSignerRepository.save(
                DocumentSigner.builder()
                        .document(docSign).email("verify-otp@test.com")
                        .name("VerifyOTP Signer").phone("+33600000005")
                        .status(SignerStatus.PENDING)
                        .signatureToken("test-token-verify-otp")
                        .otpCode("123456")
                        .otpExpiresAt(LocalDateTime.now().plusHours(24))
                        .otpVerified(false)
                        .otpAttempts(0)
                        .build());
        TOKEN_VERIFY_OTP = "test-token-verify-otp";

        // Signer for POST /sign/{token} (OTP already verified)
        documentSignerRepository.save(
                DocumentSigner.builder()
                        .document(docSign).email("sign@test.com")
                        .name("Sign Signer").phone("+33600000006")
                        .status(SignerStatus.PENDING)
                        .signatureToken("test-token-sign")
                        .otpCode("654321")
                        .otpExpiresAt(LocalDateTime.now().plusHours(24))
                        .otpVerified(true)
                        .otpAttempts(1)
                        .build());
        TOKEN_SIGN = "test-token-sign";

        // 6. DRAFT document — dedicated for DELETE
        Document docForDelete = createTestDocument(user, "Test Document Delete",
                "test_delete.pdf", DocumentStatus.DRAFT);
        DOC_FOR_DELETE_ID = docForDelete.getId().toString();

        log.info("  📄  Documents seeded (6 docs, 8 signers)");
    }

    // ── Payments — separate entities for read, confirm, cancel ─────────

    private void seedPayments(User user) {
        // 1. Payment for GET (read-only)
        String readIntentId = "pi_test_read_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        Payment payRead = paymentRepository.save(
                Payment.builder()
                        .user(user).amount(new BigDecimal("49.99")).currency("EUR")
                        .description("Payment for reading")
                        .status(PaymentStatus.PENDING)
                        .stripePaymentIntentId(readIntentId)
                        .providerReference(readIntentId)
                        .build()
        );
        PAYMENT_READ_ID = payRead.getId().toString();

        // 2. Payment for POST /confirm
        String confirmIntentId = "pi_test_confirm_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        paymentRepository.save(
                Payment.builder()
                        .user(user).amount(new BigDecimal("29.99")).currency("EUR")
                        .description("Payment for confirm test")
                        .status(PaymentStatus.PENDING)
                        .stripePaymentIntentId(confirmIntentId)
                        .providerReference(confirmIntentId)
                        .build()
        );
        PAYMENT_CONFIRM_INTENT_ID = confirmIntentId;

        // 3. Payment for POST /cancel
        Payment payCancel = paymentRepository.save(
                Payment.builder()
                        .user(user).amount(new BigDecimal("19.99")).currency("EUR")
                        .description("Payment for cancel test")
                        .status(PaymentStatus.PENDING)
                        .stripePaymentIntentId("pi_test_cancel_placeholder")
                        .providerReference("pi_test_cancel_placeholder")
                        .build()
        );
        PAYMENT_CANCEL_ID = payCancel.getId().toString();

        log.info("  💳  Payments seeded (3 payments: read, confirm, cancel)");
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    private AccountApplication saveApp(User user, AccountType type, String ref, ApplicationStatus status) {
        AccountApplication app = AccountApplication.builder()
                .user(user)
                .accountType(type)
                .referenceNumber(ref)
                .status(status)
                .dateOfBirth(LocalDate.of(1990, 5, 15))
                .phoneNumber("+33612345678")
                .nationality("Française")
                .addressLine1("123 Rue de la Paix")
                .addressLine2("Apt 42")
                .city("Paris")
                .postalCode("75001")
                .country("France")
                .employmentStatus(EmploymentStatus.EMPLOYED)
                .employerName("ACME Corp")
                .jobTitle("Software Engineer")
                .monthlyIncome(new BigDecimal("3500.00"))
                .build();
        return applicationRepository.save(app);
    }

    private Document createTestDocument(User owner, String title, String fileName,
                                        DocumentStatus status) throws IOException {
        Path uploadDir = Paths.get("uploads/documents");
        Files.createDirectories(uploadDir);
        String uniqueName = UUID.randomUUID() + "_" + fileName;
        Path filePath = uploadDir.resolve(uniqueName);
        try (OutputStream os = Files.newOutputStream(filePath)) {
            os.write(("%PDF-1.4\n1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n"
                    + "2 0 obj<</Type/Pages/Count 1/Kids[3 0 R]>>endobj\n"
                    + "3 0 obj<</Type/Page/Parent 2 0 R/MediaBox[0 0 612 792]>>endobj\n"
                    + "xref\n0 4\ntrailer<</Root 1 0 R/Size 4>>\nstartxref\n0\n%%EOF")
                    .getBytes());
        }
        return documentRepository.save(
                Document.builder()
                        .title(title).description("Test document for Green Score Analyzer")
                        .fileName(fileName).contentType("application/pdf")
                        .filePath(filePath.toString()).status(status).owner(owner)
                        .build()
        );
    }

    private KycDocument createKycDocument(AccountApplication app, KycDocumentType type,
                                          String fileName) throws IOException {
        Path uploadDir = Paths.get("uploads/kyc");
        Files.createDirectories(uploadDir);
        String uniqueName = UUID.randomUUID() + "_" + fileName;
        Path filePath = uploadDir.resolve(uniqueName);
        Files.writeString(filePath, "fake-kyc-content-for-testing");
        return kycDocumentRepository.save(
                KycDocument.builder()
                        .application(app).documentType(type).fileName(fileName)
                        .filePath(filePath.toString()).contentType("application/pdf")
                        .status(KycDocumentStatus.PENDING)
                        .ocrStatus(OcrVerificationStatus.NOT_AVAILABLE)
                        .build()
        );
    }

    private void createApprovedKyc(AccountApplication app, KycDocumentType type,
                                   String fileName) throws IOException {
        KycDocument kyc = createKycDocument(app, type, fileName);
        kyc.setStatus(KycDocumentStatus.APPROVED);
        kyc.setOcrStatus(OcrVerificationStatus.VERIFIED);
        kyc.setOcrMatchScore(95);
        kyc.setDocumentTypeValid(true);
        kycDocumentRepository.save(kyc);
    }

    private void addHistory(AccountApplication app, ApplicationStatus to,
                            String comment, User user) {
        statusHistoryRepository.save(
                ApplicationStatusHistory.builder()
                        .application(app).fromStatus(null).toStatus(to.name())
                        .changedBy(user).comment(comment)
                        .build()
        );
    }
}

