package com.esign.payment.service;

import com.esign.payment.dto.request.CreateAccountApplicationRequest;
import com.esign.payment.dto.request.UpdateAccountApplicationRequest;
import com.esign.payment.dto.request.UploadKycDocumentRequest;
import com.esign.payment.dto.response.AccountApplicationResponse;
import com.esign.payment.dto.response.AccountTypeResponse;
import com.esign.payment.dto.response.KycDocumentResponse;
import com.esign.payment.dto.response.OcrVerificationDetail;
import com.esign.payment.model.*;
import com.esign.payment.model.enums.*;
import com.esign.payment.repository.*;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AccountApplicationService}.
 * All external dependencies are mocked.
 */
@ExtendWith(MockitoExtension.class)
class AccountApplicationServiceTest {

    @InjectMocks
    private AccountApplicationService service;

    @Mock private AccountApplicationRepository applicationRepository;
    @Mock private AccountTypeRepository accountTypeRepository;
    @Mock private KycDocumentRepository kycDocumentRepository;
    @Mock private ApplicationStatusHistoryRepository statusHistoryRepository;
    @Mock private DocumentRepository documentRepository;
    @Mock private DocumentSignerRepository documentSignerRepository;
    @Mock private KeycloakUserService keycloakUserService;
    @Mock private EmailService emailService;
    @Mock private OcrService ocrService;
    @Mock private ContractPdfService contractPdfService;

    // ── Shared test fixtures ──

    private User testUser;
    private AccountType checkingType;
    private AccountApplication draftApp;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(UUID.randomUUID())
                .keycloakId("kc-test-id")
                .email("test@esignpay.com")
                .firstName("Test")
                .lastName("User")
                .role(UserRole.ROLE_USER)
                .build();

        checkingType = AccountType.builder()
                .id(UUID.randomUUID())
                .code("CHECKING")
                .label("Compte Courant")
                .description("Compte courant avec carte bancaire Visa")
                .monthlyFee(BigDecimal.valueOf(2.00))
                .isActive(true)
                .build();

        draftApp = AccountApplication.builder()
                .id(UUID.randomUUID())
                .user(testUser)
                .accountType(checkingType)
                .referenceNumber("ACC-20260408-0001")
                .status(ApplicationStatus.DRAFT)
                .dateOfBirth(LocalDate.of(1990, 5, 15))
                .phoneNumber("+33612345678")
                .nationality("Française")
                .addressLine1("42 rue de la Paix")
                .city("Paris")
                .postalCode("75002")
                .country("France")
                .employmentStatus(EmploymentStatus.EMPLOYED)
                .monthlyIncome(BigDecimal.valueOf(4500))
                .kycDocuments(new ArrayList<>())
                .statusHistory(new ArrayList<>())
                .build();

        // Common lenient stubs used by toResponse() / toKycResponse()
        lenient().when(statusHistoryRepository.findByApplicationIdOrderByChangedAtDesc(any())).thenReturn(List.of());
        lenient().when(ocrService.deserializeDetails(any())).thenReturn(List.of());
        lenient().when(ocrService.deserializeWarnings(any())).thenReturn(List.of());
    }

    // ═══════════════════════════════════════════════════════════════════
    // getActiveAccountTypes
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("getActiveAccountTypes — returns mapped list of active account types")
    void getActiveAccountTypes_returnsMappedList() {
        AccountType savings = AccountType.builder()
                .id(UUID.randomUUID()).code("SAVINGS").label("Livret Épargne")
                .monthlyFee(BigDecimal.ZERO).isActive(true).build();

        when(accountTypeRepository.findByIsActiveTrue()).thenReturn(List.of(checkingType, savings));

        List<AccountTypeResponse> result = service.getActiveAccountTypes();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getCode()).isEqualTo("CHECKING");
        assertThat(result.get(1).getCode()).isEqualTo("SAVINGS");
        verify(accountTypeRepository).findByIsActiveTrue();
    }

    @Test
    @DisplayName("getActiveAccountTypes — returns empty list when no active types")
    void getActiveAccountTypes_emptyList() {
        when(accountTypeRepository.findByIsActiveTrue()).thenReturn(List.of());

        List<AccountTypeResponse> result = service.getActiveAccountTypes();

        assertThat(result).isEmpty();
    }

    // ═══════════════════════════════════════════════════════════════════
    // createApplication
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("createApplication — creates application with all fields")
    void createApplication_success() {
        CreateAccountApplicationRequest request = new CreateAccountApplicationRequest();
        request.setAccountTypeCode("CHECKING");
        request.setDateOfBirth("1990-05-15");
        request.setPhoneNumber("+33612345678");
        request.setNationality("Française");
        request.setAddressLine1("42 rue de la Paix");
        request.setCity("Paris");
        request.setPostalCode("75002");
        request.setCountry("France");
        request.setEmploymentStatus("EMPLOYED");
        request.setMonthlyIncome(BigDecimal.valueOf(4500));

        when(keycloakUserService.getCurrentUser()).thenReturn(testUser);
        when(accountTypeRepository.findByCode("CHECKING")).thenReturn(Optional.of(checkingType));
        when(applicationRepository.save(any(AccountApplication.class))).thenAnswer(inv -> {
            AccountApplication app = inv.getArgument(0);
            if (app.getId() == null) app.setId(UUID.randomUUID());
            return app;
        });
        when(statusHistoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AccountApplicationResponse result = service.createApplication(request);

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(ApplicationStatus.DRAFT);
        assertThat(result.getAccountType().getCode()).isEqualTo("CHECKING");
        assertThat(result.getCity()).isEqualTo("Paris");
        assertThat(result.getDateOfBirth()).isEqualTo(LocalDate.of(1990, 5, 15));

        verify(applicationRepository).save(any(AccountApplication.class));
        verify(statusHistoryRepository).save(any(ApplicationStatusHistory.class));
    }

    @Test
    @DisplayName("createApplication — throws when account type not found")
    void createApplication_accountTypeNotFound() {
        CreateAccountApplicationRequest request = new CreateAccountApplicationRequest();
        request.setAccountTypeCode("UNKNOWN");

        when(keycloakUserService.getCurrentUser()).thenReturn(testUser);
        when(accountTypeRepository.findByCode("UNKNOWN")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createApplication(request))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("UNKNOWN");
    }

    // ═══════════════════════════════════════════════════════════════════
    // updateApplication
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("updateApplication — updates fields on a DRAFT application")
    void updateApplication_success() {
        UpdateAccountApplicationRequest request = new UpdateAccountApplicationRequest();
        request.setCity("Lyon");
        request.setPostalCode("69001");

        when(applicationRepository.findById(draftApp.getId())).thenReturn(Optional.of(draftApp));
        when(keycloakUserService.getCurrentUser()).thenReturn(testUser);
        when(applicationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AccountApplicationResponse result = service.updateApplication(draftApp.getId(), request);

        assertThat(result.getCity()).isEqualTo("Lyon");
        assertThat(result.getPostalCode()).isEqualTo("69001");
    }

    @Test
    @DisplayName("updateApplication — changes account type when provided")
    void updateApplication_changesAccountType() {
        AccountType premium = AccountType.builder()
                .id(UUID.randomUUID()).code("PREMIUM").label("Compte Premium")
                .monthlyFee(BigDecimal.valueOf(12.90)).build();

        UpdateAccountApplicationRequest request = new UpdateAccountApplicationRequest();
        request.setAccountTypeCode("PREMIUM");

        when(applicationRepository.findById(draftApp.getId())).thenReturn(Optional.of(draftApp));
        when(keycloakUserService.getCurrentUser()).thenReturn(testUser);
        when(accountTypeRepository.findByCode("PREMIUM")).thenReturn(Optional.of(premium));
        when(applicationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AccountApplicationResponse result = service.updateApplication(draftApp.getId(), request);

        assertThat(result.getAccountType().getCode()).isEqualTo("PREMIUM");
    }

    @Test
    @DisplayName("updateApplication — throws when application is not DRAFT")
    void updateApplication_notDraft() {
        draftApp.setStatus(ApplicationStatus.SUBMITTED);
        UpdateAccountApplicationRequest request = new UpdateAccountApplicationRequest();

        when(applicationRepository.findById(draftApp.getId())).thenReturn(Optional.of(draftApp));
        when(keycloakUserService.getCurrentUser()).thenReturn(testUser);

        assertThatThrownBy(() -> service.updateApplication(draftApp.getId(), request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("draft");
    }

    @Test
    @DisplayName("updateApplication — throws when user is not owner")
    void updateApplication_notOwner() {
        User otherUser = User.builder().id(UUID.randomUUID()).build();
        UpdateAccountApplicationRequest request = new UpdateAccountApplicationRequest();

        when(applicationRepository.findById(draftApp.getId())).thenReturn(Optional.of(draftApp));
        when(keycloakUserService.getCurrentUser()).thenReturn(otherUser);

        assertThatThrownBy(() -> service.updateApplication(draftApp.getId(), request))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ═══════════════════════════════════════════════════════════════════
    // getApplication / getMyApplicationsPaged
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("getApplication — returns response for owned application")
    void getApplication_success() {
        when(applicationRepository.findById(draftApp.getId())).thenReturn(Optional.of(draftApp));
        when(keycloakUserService.getCurrentUser()).thenReturn(testUser);

        AccountApplicationResponse result = service.getApplication(draftApp.getId());

        assertThat(result.getId()).isEqualTo(draftApp.getId());
        assertThat(result.getReferenceNumber()).isEqualTo("ACC-20260408-0001");
    }

    @Test
    @DisplayName("getApplication — throws when not found")
    void getApplication_notFound() {
        UUID randomId = UUID.randomUUID();
        when(applicationRepository.findById(randomId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getApplication(randomId))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @DisplayName("getMyApplicationsPaged — returns paged results")
    void getMyApplicationsPaged_success() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<AccountApplication> page = new PageImpl<>(List.of(draftApp), pageable, 1);

        when(keycloakUserService.getCurrentUser()).thenReturn(testUser);
        when(applicationRepository.findByUserId(testUser.getId(), pageable)).thenReturn(page);

        Page<AccountApplicationResponse> result = service.getMyApplicationsPaged(pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getId()).isEqualTo(draftApp.getId());
    }

    @Test
    @DisplayName("getApplicationsChangedSince — returns applications updated after timestamp")
    void getApplicationsChangedSince_success() {
        LocalDateTime since = LocalDateTime.of(2026, 1, 1, 0, 0);
        when(keycloakUserService.getCurrentUser()).thenReturn(testUser);
        when(applicationRepository.findByUserIdAndUpdatedAtAfterOrderByUpdatedAtDesc(testUser.getId(), since))
                .thenReturn(List.of(draftApp));

        var result = service.getApplicationsChangedSince(since);

        assertThat(result).hasSize(1);
    }

    // ═══════════════════════════════════════════════════════════════════
    // deleteApplication
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("deleteApplication — deletes a DRAFT application")
    void deleteApplication_success() {
        when(applicationRepository.findById(draftApp.getId())).thenReturn(Optional.of(draftApp));
        when(keycloakUserService.getCurrentUser()).thenReturn(testUser);

        service.deleteApplication(draftApp.getId());

        verify(applicationRepository).delete(draftApp);
    }

    @Test
    @DisplayName("deleteApplication — throws when not DRAFT")
    void deleteApplication_notDraft() {
        draftApp.setStatus(ApplicationStatus.KYC_PENDING);

        when(applicationRepository.findById(draftApp.getId())).thenReturn(Optional.of(draftApp));
        when(keycloakUserService.getCurrentUser()).thenReturn(testUser);

        assertThatThrownBy(() -> service.deleteApplication(draftApp.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("draft");
    }

    @Test
    @DisplayName("deleteApplication — throws when user is not owner")
    void deleteApplication_notOwner() {
        User otherUser = User.builder().id(UUID.randomUUID()).build();

        when(applicationRepository.findById(draftApp.getId())).thenReturn(Optional.of(draftApp));
        when(keycloakUserService.getCurrentUser()).thenReturn(otherUser);

        assertThatThrownBy(() -> service.deleteApplication(draftApp.getId()))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ═══════════════════════════════════════════════════════════════════
    // submitApplication
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("submitApplication — submits with valid fields and KYC docs, transitions to KYC_PENDING")
    void submitApplication_success() {
        // Add required KYC docs
        KycDocument idDoc = KycDocument.builder()
                .id(UUID.randomUUID()).application(draftApp)
                .documentType(KycDocumentType.ID_CARD).fileName("id.jpg")
                .filePath("uploads/kyc/id.jpg").contentType("image/jpeg")
                .status(KycDocumentStatus.PENDING).build();
        KycDocument addressDoc = KycDocument.builder()
                .id(UUID.randomUUID()).application(draftApp)
                .documentType(KycDocumentType.PROOF_OF_ADDRESS).fileName("address.pdf")
                .filePath("uploads/kyc/address.pdf").contentType("application/pdf")
                .status(KycDocumentStatus.PENDING).build();
        draftApp.setKycDocuments(new ArrayList<>(List.of(idDoc, addressDoc)));

        when(applicationRepository.findById(draftApp.getId())).thenReturn(Optional.of(draftApp));
        when(keycloakUserService.getCurrentUser()).thenReturn(testUser);
        when(applicationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(statusHistoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AccountApplicationResponse result = service.submitApplication(draftApp.getId());

        // KYC docs are PENDING, so status should be KYC_PENDING (not auto-verified)
        assertThat(result.getStatus()).isEqualTo(ApplicationStatus.KYC_PENDING);
        verify(emailService).sendStatusUpdateEmail(eq(testUser.getEmail()), eq("Test"),
                eq("ACC-20260408-0001"), contains("En attente"));
    }

    @Test
    @DisplayName("submitApplication — auto-verifies KYC when all docs are APPROVED")
    void submitApplication_autoVerifiesKyc() {
        KycDocument idDoc = KycDocument.builder()
                .id(UUID.randomUUID()).application(draftApp)
                .documentType(KycDocumentType.ID_CARD).fileName("id.jpg")
                .filePath("uploads/kyc/id.jpg").contentType("image/jpeg")
                .status(KycDocumentStatus.APPROVED).build();
        KycDocument addressDoc = KycDocument.builder()
                .id(UUID.randomUUID()).application(draftApp)
                .documentType(KycDocumentType.PROOF_OF_ADDRESS).fileName("address.pdf")
                .filePath("uploads/kyc/address.pdf").contentType("application/pdf")
                .status(KycDocumentStatus.APPROVED).build();
        draftApp.setKycDocuments(new ArrayList<>(List.of(idDoc, addressDoc)));

        when(applicationRepository.findById(draftApp.getId())).thenReturn(Optional.of(draftApp));
        when(keycloakUserService.getCurrentUser()).thenReturn(testUser);
        when(applicationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(statusHistoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AccountApplicationResponse result = service.submitApplication(draftApp.getId());

        assertThat(result.getStatus()).isEqualTo(ApplicationStatus.KYC_VERIFIED);
        verify(emailService).sendStatusUpdateEmail(eq(testUser.getEmail()), eq("Test"),
                eq("ACC-20260408-0001"), contains("vérifié"));
    }

    @Test
    @DisplayName("submitApplication — throws when not DRAFT")
    void submitApplication_notDraft() {
        draftApp.setStatus(ApplicationStatus.SUBMITTED);

        when(applicationRepository.findById(draftApp.getId())).thenReturn(Optional.of(draftApp));
        when(keycloakUserService.getCurrentUser()).thenReturn(testUser);

        assertThatThrownBy(() -> service.submitApplication(draftApp.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("draft");
    }

    @Test
    @DisplayName("submitApplication — throws when date of birth is missing")
    void submitApplication_missingDob() {
        draftApp.setDateOfBirth(null);
        // Add required KYC docs so it doesn't fail on KYC validation first
        draftApp.setKycDocuments(new ArrayList<>(List.of(
                KycDocument.builder().documentType(KycDocumentType.ID_CARD)
                        .fileName("f").filePath("p").contentType("c").application(draftApp).build(),
                KycDocument.builder().documentType(KycDocumentType.PROOF_OF_ADDRESS)
                        .fileName("f").filePath("p").contentType("c").application(draftApp).build()
        )));

        when(applicationRepository.findById(draftApp.getId())).thenReturn(Optional.of(draftApp));
        when(keycloakUserService.getCurrentUser()).thenReturn(testUser);

        assertThatThrownBy(() -> service.submitApplication(draftApp.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Date of birth");
    }

    @Test
    @DisplayName("submitApplication — throws when missing ID document KYC")
    void submitApplication_missingIdDoc() {
        // Only PROOF_OF_ADDRESS, no ID/PASSPORT
        draftApp.setKycDocuments(new ArrayList<>(List.of(
                KycDocument.builder().documentType(KycDocumentType.PROOF_OF_ADDRESS)
                        .fileName("f").filePath("p").contentType("c").application(draftApp).build()
        )));

        when(applicationRepository.findById(draftApp.getId())).thenReturn(Optional.of(draftApp));
        when(keycloakUserService.getCurrentUser()).thenReturn(testUser);

        assertThatThrownBy(() -> service.submitApplication(draftApp.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("pièce d'identité");
    }

    @Test
    @DisplayName("submitApplication — throws when missing proof of address KYC")
    void submitApplication_missingAddressDoc() {
        draftApp.setKycDocuments(new ArrayList<>(List.of(
                KycDocument.builder().documentType(KycDocumentType.ID_CARD)
                        .fileName("f").filePath("p").contentType("c").application(draftApp).build()
        )));

        when(applicationRepository.findById(draftApp.getId())).thenReturn(Optional.of(draftApp));
        when(keycloakUserService.getCurrentUser()).thenReturn(testUser);

        assertThatThrownBy(() -> service.submitApplication(draftApp.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("justificatif de domicile");
    }

    // ═══════════════════════════════════════════════════════════════════
    // uploadKycDocument
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("uploadKycDocument — uploads KYC doc, runs OCR, returns response")
    void uploadKycDocument_success() {
        UploadKycDocumentRequest request = new UploadKycDocumentRequest();
        request.setDocumentType("ID_CARD");
        request.setFileName("carte-id.jpg");
        request.setContentType("image/jpeg");
        request.setFileContent(Base64.getEncoder().encodeToString("fake-image".getBytes()));

        OcrService.OcrResult ocrResult = new OcrService.OcrResult(
                OcrVerificationStatus.VERIFIED, "extracted text", 85,
                List.of(), true, List.of());

        when(applicationRepository.findById(draftApp.getId())).thenReturn(Optional.of(draftApp));
        when(keycloakUserService.getCurrentUser()).thenReturn(testUser);
        when(kycDocumentRepository.save(any(KycDocument.class))).thenAnswer(inv -> {
            KycDocument doc = inv.getArgument(0);
            if (doc.getId() == null) doc.setId(UUID.randomUUID());
            return doc;
        });
        when(ocrService.verifyDocument(any(), eq("image/jpeg"), eq(KycDocumentType.ID_CARD), eq(draftApp)))
                .thenReturn(ocrResult);
        when(ocrService.serializeDetails(any())).thenReturn("[]");
        when(ocrService.serializeWarnings(any())).thenReturn("[]");

        KycDocumentResponse result = service.uploadKycDocument(draftApp.getId(), request);

        assertThat(result).isNotNull();
        assertThat(result.getDocumentType()).isEqualTo(KycDocumentType.ID_CARD);
        assertThat(result.getFileName()).isEqualTo("carte-id.jpg");
        // OCR verified with score >= 60 → auto-approved
        assertThat(result.getStatus()).isEqualTo(KycDocumentStatus.APPROVED);
        verify(kycDocumentRepository, atLeast(2)).save(any(KycDocument.class));
    }

    @Test
    @DisplayName("uploadKycDocument — OCR failure sets FAILED status gracefully")
    void uploadKycDocument_ocrFailure() {
        UploadKycDocumentRequest request = new UploadKycDocumentRequest();
        request.setDocumentType("ID_CARD");
        request.setFileName("bad-image.jpg");
        request.setContentType("image/jpeg");
        request.setFileContent(Base64.getEncoder().encodeToString("bad-data".getBytes()));

        when(applicationRepository.findById(draftApp.getId())).thenReturn(Optional.of(draftApp));
        when(keycloakUserService.getCurrentUser()).thenReturn(testUser);
        when(kycDocumentRepository.save(any(KycDocument.class))).thenAnswer(inv -> {
            KycDocument doc = inv.getArgument(0);
            if (doc.getId() == null) doc.setId(UUID.randomUUID());
            return doc;
        });
        when(ocrService.verifyDocument(any(), any(), any(), any()))
                .thenThrow(new RuntimeException("OCR engine crash"));
        when(ocrService.serializeWarnings(any())).thenReturn("[\"Erreur OCR\"]");

        KycDocumentResponse result = service.uploadKycDocument(draftApp.getId(), request);

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(KycDocumentStatus.PENDING);
        // KYC doc is saved with OCR FAILED but the upload still succeeds
        verify(kycDocumentRepository, atLeast(2)).save(any(KycDocument.class));
    }

    // ═══════════════════════════════════════════════════════════════════
    // deleteKycDocument
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("deleteKycDocument — deletes KYC doc belonging to owned application")
    void deleteKycDocument_success() {
        UUID kycId = UUID.randomUUID();
        KycDocument kyc = KycDocument.builder()
                .id(kycId).application(draftApp)
                .documentType(KycDocumentType.ID_CARD).fileName("id.jpg")
                .filePath("p").contentType("c").build();

        when(applicationRepository.findById(draftApp.getId())).thenReturn(Optional.of(draftApp));
        when(keycloakUserService.getCurrentUser()).thenReturn(testUser);
        when(kycDocumentRepository.findById(kycId)).thenReturn(Optional.of(kyc));

        service.deleteKycDocument(draftApp.getId(), kycId);

        verify(kycDocumentRepository).delete(kyc);
    }

    @Test
    @DisplayName("deleteKycDocument — throws when KYC doc not found")
    void deleteKycDocument_notFound() {
        UUID kycId = UUID.randomUUID();

        when(applicationRepository.findById(draftApp.getId())).thenReturn(Optional.of(draftApp));
        when(keycloakUserService.getCurrentUser()).thenReturn(testUser);
        when(kycDocumentRepository.findById(kycId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteKycDocument(draftApp.getId(), kycId))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @DisplayName("deleteKycDocument — throws when KYC doc belongs to different application")
    void deleteKycDocument_wrongApplication() {
        AccountApplication otherApp = AccountApplication.builder()
                .id(UUID.randomUUID()).user(testUser).build();
        UUID kycId = UUID.randomUUID();
        KycDocument kyc = KycDocument.builder()
                .id(kycId).application(otherApp)
                .documentType(KycDocumentType.ID_CARD).fileName("id.jpg")
                .filePath("p").contentType("c").build();

        when(applicationRepository.findById(draftApp.getId())).thenReturn(Optional.of(draftApp));
        when(keycloakUserService.getCurrentUser()).thenReturn(testUser);
        when(kycDocumentRepository.findById(kycId)).thenReturn(Optional.of(kyc));

        assertThatThrownBy(() -> service.deleteKycDocument(draftApp.getId(), kycId))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ═══════════════════════════════════════════════════════════════════
    // generateContract
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("generateContract — generates contract for KYC_VERIFIED application")
    void generateContract_success() {
        draftApp.setStatus(ApplicationStatus.KYC_VERIFIED);

        when(applicationRepository.findById(draftApp.getId())).thenReturn(Optional.of(draftApp));
        when(keycloakUserService.getCurrentUser()).thenReturn(testUser);
        when(contractPdfService.generateContractPdf(draftApp)).thenReturn("uploads/contracts/contrat.pdf");
        when(documentRepository.save(any(Document.class))).thenAnswer(inv -> {
            Document doc = inv.getArgument(0);
            if (doc.getId() == null) doc.setId(UUID.randomUUID());
            return doc;
        });
        when(documentSignerRepository.save(any(DocumentSigner.class))).thenAnswer(inv -> inv.getArgument(0));
        when(applicationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(statusHistoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AccountApplicationResponse result = service.generateContract(draftApp.getId());

        assertThat(result.getStatus()).isEqualTo(ApplicationStatus.CONTRACT_PENDING);
        assertThat(result.getContractDocumentId()).isNotNull();
        verify(contractPdfService).generateContractPdf(draftApp);
        verify(documentSignerRepository).save(any(DocumentSigner.class));
        verify(emailService).sendContractSigningEmail(eq(testUser.getEmail()), eq("Test"), any(), eq("ACC-20260408-0001"));
    }

    @Test
    @DisplayName("generateContract — auto-verifies KYC_PENDING before generating")
    void generateContract_autoVerifiesKycPending() {
        draftApp.setStatus(ApplicationStatus.KYC_PENDING);

        when(applicationRepository.findById(draftApp.getId())).thenReturn(Optional.of(draftApp));
        when(keycloakUserService.getCurrentUser()).thenReturn(testUser);
        when(contractPdfService.generateContractPdf(any())).thenReturn("uploads/contracts/contrat.pdf");
        when(documentRepository.save(any(Document.class))).thenAnswer(inv -> {
            Document doc = inv.getArgument(0);
            if (doc.getId() == null) doc.setId(UUID.randomUUID());
            return doc;
        });
        when(documentSignerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(applicationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(statusHistoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AccountApplicationResponse result = service.generateContract(draftApp.getId());

        assertThat(result.getStatus()).isEqualTo(ApplicationStatus.CONTRACT_PENDING);
    }

    @Test
    @DisplayName("generateContract — throws when status is DRAFT (not KYC ready)")
    void generateContract_throwsWhenDraft() {
        draftApp.setStatus(ApplicationStatus.DRAFT);

        when(applicationRepository.findById(draftApp.getId())).thenReturn(Optional.of(draftApp));

        assertThatThrownBy(() -> service.generateContract(draftApp.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("KYC must be verified");
    }

    // ═══════════════════════════════════════════════════════════════════
    // regenerateContract
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("regenerateContract — deletes old contract and generates new one")
    void regenerateContract_success() {
        Document oldContract = Document.builder()
                .id(UUID.randomUUID()).title("Old Contract").fileName("old.pdf")
                .contentType("application/pdf").filePath("path")
                .status(DocumentStatus.PENDING_SIGNATURE).owner(testUser).build();
        draftApp.setStatus(ApplicationStatus.CONTRACT_PENDING);
        draftApp.setContractDocument(oldContract);

        when(applicationRepository.findById(draftApp.getId())).thenReturn(Optional.of(draftApp));
        when(keycloakUserService.getCurrentUser()).thenReturn(testUser);
        when(applicationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(statusHistoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(contractPdfService.generateContractPdf(any())).thenReturn("uploads/contracts/new.pdf");
        when(documentRepository.save(any(Document.class))).thenAnswer(inv -> {
            Document doc = inv.getArgument(0);
            if (doc.getId() == null) doc.setId(UUID.randomUUID());
            return doc;
        });
        when(documentSignerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AccountApplicationResponse result = service.regenerateContract(draftApp.getId());

        assertThat(result.getStatus()).isEqualTo(ApplicationStatus.CONTRACT_PENDING);
        verify(documentRepository).delete(oldContract);
        verify(contractPdfService).generateContractPdf(any());
    }

    @Test
    @DisplayName("regenerateContract — throws when status is not CONTRACT_PENDING")
    void regenerateContract_wrongStatus() {
        draftApp.setStatus(ApplicationStatus.DRAFT);

        when(applicationRepository.findById(draftApp.getId())).thenReturn(Optional.of(draftApp));
        when(keycloakUserService.getCurrentUser()).thenReturn(testUser);

        assertThatThrownBy(() -> service.regenerateContract(draftApp.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CONTRACT_PENDING");
    }

    // ═══════════════════════════════════════════════════════════════════
    // onContractSigned
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("onContractSigned — transitions through CONTRACT_SIGNED → APPROVED → ACTIVE")
    void onContractSigned_fullTransition() {
        UUID docId = UUID.randomUUID();
        draftApp.setStatus(ApplicationStatus.CONTRACT_PENDING);

        when(applicationRepository.findByContractDocumentId(docId)).thenReturn(Optional.of(draftApp));
        when(applicationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(statusHistoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(keycloakUserService.getCurrentUser()).thenReturn(testUser);

        service.onContractSigned(docId);

        // Verify final status is ACTIVE
        assertThat(draftApp.getStatus()).isEqualTo(ApplicationStatus.ACTIVE);
        assertThat(draftApp.getApprovedAt()).isNotNull();

        // 3 status history entries: CONTRACT_SIGNED, APPROVED, ACTIVE
        verify(statusHistoryRepository, times(3)).save(any(ApplicationStatusHistory.class));
        verify(emailService).sendStatusUpdateEmail(eq(testUser.getEmail()), eq("Test"),
                eq("ACC-20260408-0001"), eq("Compte actif"));
    }

    @Test
    @DisplayName("onContractSigned — no-op when application is not in CONTRACT_PENDING")
    void onContractSigned_wrongStatus() {
        UUID docId = UUID.randomUUID();
        draftApp.setStatus(ApplicationStatus.DRAFT);

        when(applicationRepository.findByContractDocumentId(docId)).thenReturn(Optional.of(draftApp));

        service.onContractSigned(docId);

        // No status transitions should occur
        verify(applicationRepository, never()).save(any());
        verify(emailService, never()).sendStatusUpdateEmail(any(), any(), any(), any());
    }

    @Test
    @DisplayName("onContractSigned — no-op when no application found for document")
    void onContractSigned_noApplication() {
        UUID docId = UUID.randomUUID();

        when(applicationRepository.findByContractDocumentId(docId)).thenReturn(Optional.empty());

        service.onContractSigned(docId);

        verify(applicationRepository, never()).save(any());
    }

    // ═══════════════════════════════════════════════════════════════════
    // getMyApplications (non-paged)
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("getMyApplications — returns list of user's applications")
    void getMyApplications_success() {
        when(keycloakUserService.getCurrentUser()).thenReturn(testUser);
        when(applicationRepository.findByUserIdOrderByCreatedAtDesc(testUser.getId()))
                .thenReturn(List.of(draftApp));

        var result = service.getMyApplications();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getReferenceNumber()).isEqualTo("ACC-20260408-0001");
    }

    // ═══════════════════════════════════════════════════════════════════
    // Edge cases & validation
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("submitApplication — throws when address is missing")
    void submitApplication_missingAddress() {
        draftApp.setAddressLine1(null);
        addMinimalKycDocs(draftApp);

        when(applicationRepository.findById(draftApp.getId())).thenReturn(Optional.of(draftApp));
        when(keycloakUserService.getCurrentUser()).thenReturn(testUser);

        assertThatThrownBy(() -> service.submitApplication(draftApp.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Address is required");
    }

    @Test
    @DisplayName("submitApplication — throws when city is missing")
    void submitApplication_missingCity() {
        draftApp.setCity(null);
        addMinimalKycDocs(draftApp);

        when(applicationRepository.findById(draftApp.getId())).thenReturn(Optional.of(draftApp));
        when(keycloakUserService.getCurrentUser()).thenReturn(testUser);

        assertThatThrownBy(() -> service.submitApplication(draftApp.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("City is required");
    }

    @Test
    @DisplayName("submitApplication — throws when postal code is missing")
    void submitApplication_missingPostalCode() {
        draftApp.setPostalCode(null);
        addMinimalKycDocs(draftApp);

        when(applicationRepository.findById(draftApp.getId())).thenReturn(Optional.of(draftApp));
        when(keycloakUserService.getCurrentUser()).thenReturn(testUser);

        assertThatThrownBy(() -> service.submitApplication(draftApp.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Postal code is required");
    }

    @Test
    @DisplayName("submitApplication — throws when country is missing")
    void submitApplication_missingCountry() {
        draftApp.setCountry(null);
        addMinimalKycDocs(draftApp);

        when(applicationRepository.findById(draftApp.getId())).thenReturn(Optional.of(draftApp));
        when(keycloakUserService.getCurrentUser()).thenReturn(testUser);

        assertThatThrownBy(() -> service.submitApplication(draftApp.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Country is required");
    }

    @Test
    @DisplayName("submitApplication — PASSPORT accepted as valid ID document")
    void submitApplication_passportAccepted() {
        KycDocument passport = KycDocument.builder()
                .documentType(KycDocumentType.PASSPORT).fileName("p").filePath("p")
                .contentType("c").application(draftApp).status(KycDocumentStatus.PENDING).build();
        KycDocument address = KycDocument.builder()
                .documentType(KycDocumentType.PROOF_OF_ADDRESS).fileName("a").filePath("a")
                .contentType("c").application(draftApp).status(KycDocumentStatus.PENDING).build();
        draftApp.setKycDocuments(new ArrayList<>(List.of(passport, address)));

        when(applicationRepository.findById(draftApp.getId())).thenReturn(Optional.of(draftApp));
        when(keycloakUserService.getCurrentUser()).thenReturn(testUser);
        when(applicationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(statusHistoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Should not throw
        AccountApplicationResponse result = service.submitApplication(draftApp.getId());
        assertThat(result.getStatus()).isEqualTo(ApplicationStatus.KYC_PENDING);
    }

    // ═══════════════════════════════════════════════════════════════════
    // Response mapping
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("toResponse — maps all fields including KYC documents and status history")
    void toResponse_mapsAllFields() {
        KycDocument kyc = KycDocument.builder()
                .id(UUID.randomUUID()).application(draftApp)
                .documentType(KycDocumentType.ID_CARD).fileName("id.jpg")
                .filePath("p").contentType("image/jpeg")
                .status(KycDocumentStatus.APPROVED)
                .ocrStatus(OcrVerificationStatus.VERIFIED)
                .ocrMatchScore(92).documentTypeValid(true)
                .build();
        draftApp.setKycDocuments(new ArrayList<>(List.of(kyc)));
        draftApp.setEmploymentStatus(EmploymentStatus.SELF_EMPLOYED);
        draftApp.setEmployerName("Freelance");
        draftApp.setJobTitle("Developer");

        ApplicationStatusHistory history = ApplicationStatusHistory.builder()
                .id(UUID.randomUUID()).application(draftApp)
                .fromStatus(null).toStatus("DRAFT")
                .comment("Created").changedAt(LocalDateTime.now()).build();

        when(applicationRepository.findById(draftApp.getId())).thenReturn(Optional.of(draftApp));
        when(keycloakUserService.getCurrentUser()).thenReturn(testUser);
        when(statusHistoryRepository.findByApplicationIdOrderByChangedAtDesc(draftApp.getId()))
                .thenReturn(List.of(history));

        AccountApplicationResponse result = service.getApplication(draftApp.getId());

        assertThat(result.getEmploymentStatus()).isEqualTo(EmploymentStatus.SELF_EMPLOYED);
        assertThat(result.getEmployerName()).isEqualTo("Freelance");
        assertThat(result.getJobTitle()).isEqualTo("Developer");
        assertThat(result.getKycDocuments()).hasSize(1);
        assertThat(result.getKycDocuments().get(0).getOcrMatchScore()).isEqualTo(92);
        assertThat(result.getStatusHistory()).hasSize(1);
        assertThat(result.getStatusHistory().get(0).getToStatus()).isEqualTo("DRAFT");
    }

    @Test
    @DisplayName("toResponse — contractDocumentId is null when no contract")
    void toResponse_noContract() {
        draftApp.setContractDocument(null);

        when(applicationRepository.findById(draftApp.getId())).thenReturn(Optional.of(draftApp));
        when(keycloakUserService.getCurrentUser()).thenReturn(testUser);

        AccountApplicationResponse result = service.getApplication(draftApp.getId());

        assertThat(result.getContractDocumentId()).isNull();
    }

    @Test
    @DisplayName("toResponse — contractDocumentId is set when contract exists")
    void toResponse_withContract() {
        UUID contractId = UUID.randomUUID();
        Document contract = Document.builder().id(contractId).title("c").fileName("c.pdf")
                .contentType("application/pdf").filePath("p")
                .status(DocumentStatus.PENDING_SIGNATURE).owner(testUser).build();
        draftApp.setContractDocument(contract);

        when(applicationRepository.findById(draftApp.getId())).thenReturn(Optional.of(draftApp));
        when(keycloakUserService.getCurrentUser()).thenReturn(testUser);

        AccountApplicationResponse result = service.getApplication(draftApp.getId());

        assertThat(result.getContractDocumentId()).isEqualTo(contractId);
    }

    // ── Helper ──

    private void addMinimalKycDocs(AccountApplication app) {
        app.setKycDocuments(new ArrayList<>(List.of(
                KycDocument.builder().documentType(KycDocumentType.ID_CARD)
                        .fileName("f").filePath("p").contentType("c").application(app).build(),
                KycDocument.builder().documentType(KycDocumentType.PROOF_OF_ADDRESS)
                        .fileName("f").filePath("p").contentType("c").application(app).build()
        )));
    }
}

