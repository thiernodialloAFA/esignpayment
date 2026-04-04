package com.esign.payment.service;

import com.esign.payment.dto.request.*;
import com.esign.payment.dto.response.*;
import com.esign.payment.model.*;
import com.esign.payment.model.enums.*;
import com.esign.payment.repository.*;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountApplicationService {

    private final AccountApplicationRepository applicationRepository;
    private final AccountTypeRepository accountTypeRepository;
    private final KycDocumentRepository kycDocumentRepository;
    private final ApplicationStatusHistoryRepository statusHistoryRepository;
    private final DocumentRepository documentRepository;
    private final DocumentSignerRepository documentSignerRepository;
    private final KeycloakUserService keycloakUserService;
    private final EmailService emailService;
    private final OcrService ocrService;
    private final ContractPdfService contractPdfService;

    private static final String KYC_UPLOAD_DIR = "uploads/kyc";

    // ── Account Types ──

    public List<AccountTypeResponse> getActiveAccountTypes() {
        return accountTypeRepository.findByIsActiveTrue().stream()
                .map(this::toAccountTypeResponse).collect(Collectors.toList());
    }

    // ── CRUD ──

    @Transactional
    public AccountApplicationResponse createApplication(CreateAccountApplicationRequest request) {
        User user = keycloakUserService.getCurrentUser();
        AccountType type = accountTypeRepository.findByCode(request.getAccountTypeCode())
                .orElseThrow(() -> new EntityNotFoundException("Account type not found: " + request.getAccountTypeCode()));

        AccountApplication app = AccountApplication.builder()
                .user(user)
                .accountType(type)
                .referenceNumber(generateReferenceNumber())
                .status(ApplicationStatus.DRAFT)
                .build();

        applyFields(app, request.getDateOfBirth(), request.getPhoneNumber(), request.getNationality(),
                request.getAddressLine1(), request.getAddressLine2(), request.getCity(),
                request.getPostalCode(), request.getCountry(), request.getEmploymentStatus(),
                request.getEmployerName(), request.getJobTitle(), request.getMonthlyIncome());

        app = applicationRepository.save(app);
        addStatusHistory(app, null, ApplicationStatus.DRAFT, "Application created");
        return toResponse(app);
    }

    @Transactional
    public AccountApplicationResponse updateApplication(UUID id, UpdateAccountApplicationRequest request) {
        AccountApplication app = findById(id);
        checkOwnership(app);
        if (app.getStatus() != ApplicationStatus.DRAFT) {
            throw new IllegalStateException("Only draft applications can be updated");
        }

        if (request.getAccountTypeCode() != null) {
            AccountType type = accountTypeRepository.findByCode(request.getAccountTypeCode())
                    .orElseThrow(() -> new EntityNotFoundException("Account type not found"));
            app.setAccountType(type);
        }

        applyFields(app, request.getDateOfBirth(), request.getPhoneNumber(), request.getNationality(),
                request.getAddressLine1(), request.getAddressLine2(), request.getCity(),
                request.getPostalCode(), request.getCountry(), request.getEmploymentStatus(),
                request.getEmployerName(), request.getJobTitle(), request.getMonthlyIncome());

        return toResponse(applicationRepository.save(app));
    }

    @Transactional(readOnly = true)
    public List<AccountApplicationResponse> getMyApplications() {
        User user = keycloakUserService.getCurrentUser();
        return applicationRepository.findByUserIdOrderByCreatedAtDesc(user.getId())
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Page<AccountApplicationResponse> getMyApplicationsPaged(Pageable pageable) {
        User user = keycloakUserService.getCurrentUser();
        return applicationRepository.findByUserId(user.getId(), pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public List<AccountApplicationResponse> getApplicationsChangedSince(LocalDateTime since) {
        User user = keycloakUserService.getCurrentUser();
        return applicationRepository.findByUserIdAndUpdatedAtAfterOrderByUpdatedAtDesc(user.getId(), since)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public AccountApplicationResponse getApplication(UUID id) {
        AccountApplication app = findById(id);
        checkOwnership(app);
        return toResponse(app);
    }

    @Transactional
    public void deleteApplication(UUID id) {
        AccountApplication app = findById(id);
        checkOwnership(app);
        if (app.getStatus() != ApplicationStatus.DRAFT) {
            throw new IllegalStateException("Only draft applications can be deleted");
        }
        applicationRepository.delete(app);
    }

    // ── Submit ──

    @Transactional
    public AccountApplicationResponse submitApplication(UUID id) {
        AccountApplication app = findById(id);
        checkOwnership(app);
        if (app.getStatus() != ApplicationStatus.DRAFT) {
            throw new IllegalStateException("Application is not in draft status");
        }
        validateRequiredFields(app);
        validateRequiredKycDocuments(app);

        app.setStatus(ApplicationStatus.SUBMITTED);
        app.setSubmittedAt(LocalDateTime.now());
        app = applicationRepository.save(app);
        addStatusHistory(app, ApplicationStatus.DRAFT, ApplicationStatus.SUBMITTED, "Application submitted");

        // Auto-transition to KYC_PENDING
        app.setStatus(ApplicationStatus.KYC_PENDING);
        app = applicationRepository.save(app);
        addStatusHistory(app, ApplicationStatus.SUBMITTED, ApplicationStatus.KYC_PENDING, "Awaiting KYC verification");

        // Check if all KYC docs are already verified → auto-verify
        tryAutoVerifyKyc(app);

        emailService.sendStatusUpdateEmail(app.getUser().getEmail(),
                app.getUser().getFirstName(), app.getReferenceNumber(),
                app.getStatus() == ApplicationStatus.KYC_VERIFIED
                        ? "Soumise - KYC vérifié automatiquement"
                        : "Soumise - En attente de vérification KYC");

        return toResponse(app);
    }

    // ── KYC ──

    @Transactional
    public KycDocumentResponse uploadKycDocument(UUID applicationId, UploadKycDocumentRequest request) {
        AccountApplication app = findById(applicationId);
        checkOwnership(app);

        KycDocumentType docType = KycDocumentType.valueOf(request.getDocumentType());
        byte[] fileBytes = Base64.getDecoder().decode(request.getFileContent());
        String filePath = saveKycFile(request.getFileName(), fileBytes);

        KycDocument kyc = KycDocument.builder()
                .application(app)
                .documentType(docType)
                .fileName(request.getFileName())
                .filePath(filePath)
                .contentType(request.getContentType())
                .status(KycDocumentStatus.PENDING)
                .ocrStatus(OcrVerificationStatus.PENDING)
                .build();

        kyc = kycDocumentRepository.save(kyc);

        // ── Run OCR verification ──
        try {
            OcrService.OcrResult result = ocrService.verifyDocument(fileBytes, request.getContentType(), docType, app);
            kyc.setExtractedText(truncate(result.extractedText(), 5000));
            kyc.setOcrStatus(result.status());
            kyc.setOcrMatchScore(result.matchScore());
            kyc.setDocumentTypeValid(result.documentTypeValid());
            kyc.setOcrDetails(ocrService.serializeDetails(result.details()));
            kyc.setOcrWarnings(ocrService.serializeWarnings(result.warnings()));

            // Auto-approve if OCR verified with sufficient confidence
            if (result.status() == OcrVerificationStatus.VERIFIED && result.matchScore() >= 60) {
                kyc.setStatus(KycDocumentStatus.APPROVED);
            }

            kyc = kycDocumentRepository.save(kyc);
            log.info("OCR verification completed for KYC doc {} – status={}, score={}",
                    kyc.getId(), result.status(), result.matchScore());
        } catch (Exception e) {
            log.error("OCR processing error for KYC doc {}", kyc.getId(), e);
            kyc.setOcrStatus(OcrVerificationStatus.FAILED);
            kyc.setOcrWarnings(ocrService.serializeWarnings(List.of("Erreur OCR: " + e.getMessage())));
            kyc = kycDocumentRepository.save(kyc);
        }

        // Check if all KYC docs are now verified → auto-transition application
        if (app.getStatus() == ApplicationStatus.KYC_PENDING) {
            tryAutoVerifyKyc(app);
        }

        return toKycResponse(kyc);
    }

    @Transactional
    public void deleteKycDocument(UUID applicationId, UUID kycId) {
        AccountApplication app = findById(applicationId);
        checkOwnership(app);
        KycDocument kyc = kycDocumentRepository.findById(kycId)
                .orElseThrow(() -> new EntityNotFoundException("KYC document not found"));
        if (!kyc.getApplication().getId().equals(applicationId)) {
            throw new AccessDeniedException("KYC document does not belong to this application");
        }
        kycDocumentRepository.delete(kyc);
    }

    // ── Contract Generation (after KYC verified) ──

    @Transactional
    public AccountApplicationResponse generateContract(UUID applicationId) {
        AccountApplication app = findById(applicationId);

        // Auto-verify KYC for now (in production, admin would verify)
        if (app.getStatus() == ApplicationStatus.KYC_PENDING) {
            app.setStatus(ApplicationStatus.KYC_VERIFIED);
            app = applicationRepository.save(app);
            addStatusHistory(app, ApplicationStatus.KYC_PENDING, ApplicationStatus.KYC_VERIFIED, "KYC documents verified");
        }

        if (app.getStatus() != ApplicationStatus.KYC_VERIFIED) {
            throw new IllegalStateException("KYC must be verified before generating contract");
        }

        // Create a contract document linked to the e-sign flow
        User user = app.getUser();

        // Generate the actual PDF file on disk
        String contractFilePath = contractPdfService.generateContractPdf(app);
        String contractFileName = "contrat_" + app.getReferenceNumber() + ".pdf";

        Document contract = Document.builder()
                .title("Contrat d'ouverture de compte - " + app.getReferenceNumber())
                .description("Contrat pour " + app.getAccountType().getLabel())
                .fileName(contractFileName)
                .contentType("application/pdf")
                .filePath(contractFilePath)
                .status(com.esign.payment.model.enums.DocumentStatus.PENDING_SIGNATURE)
                .owner(user)
                .build();
        contract = documentRepository.save(contract);

        // Add user as signer
        DocumentSigner signer = DocumentSigner.builder()
                .document(contract)
                .email(user.getEmail())
                .name(user.getFirstName() + " " + user.getLastName())
                .phone(app.getPhoneNumber())
                .status(SignerStatus.PENDING)
                .signatureToken(UUID.randomUUID().toString())
                .build();
        documentSignerRepository.save(signer);

        app.setContractDocument(contract);
        app.setStatus(ApplicationStatus.CONTRACT_PENDING);
        app = applicationRepository.save(app);
        addStatusHistory(app, ApplicationStatus.KYC_VERIFIED, ApplicationStatus.CONTRACT_PENDING, "Contract generated, awaiting signature");

        // Send email with signing link
        emailService.sendContractSigningEmail(user.getEmail(),
                user.getFirstName(), signer.getSignatureToken(), app.getReferenceNumber());

        return toResponse(app);
    }

    /**
     * Regenerates the contract for an application in CONTRACT_PENDING status.
     * Deletes the old contract and creates a fresh one with new signing tokens.
     */
    @Transactional
    public AccountApplicationResponse regenerateContract(UUID applicationId) {
        AccountApplication app = findById(applicationId);
        checkOwnership(app);

        if (app.getStatus() != ApplicationStatus.CONTRACT_PENDING) {
            throw new IllegalStateException("Contract can only be regenerated when status is CONTRACT_PENDING");
        }

        // Delete old contract document (cascade removes signers & signatures)
        Document oldContract = app.getContractDocument();
        if (oldContract != null) {
            app.setContractDocument(null);
            applicationRepository.save(app);
            documentRepository.delete(oldContract);
        }

        // Reset status to KYC_VERIFIED so generateContract() can run
        app.setStatus(ApplicationStatus.KYC_VERIFIED);
        app = applicationRepository.save(app);
        addStatusHistory(app, ApplicationStatus.CONTRACT_PENDING, ApplicationStatus.KYC_VERIFIED,
                "Contract regenerated – previous contract deleted");

        return generateContract(applicationId);
    }

    /**
     * Called when the contract document is fully signed.
     * Transitions the application to APPROVED then ACTIVE.
     */
    @Transactional
    public void onContractSigned(UUID documentId) {
        applicationRepository.findByContractDocumentId(documentId).ifPresent(app -> {
            if (app.getStatus() == ApplicationStatus.CONTRACT_PENDING) {
                app.setStatus(ApplicationStatus.CONTRACT_SIGNED);
                applicationRepository.save(app);
                addStatusHistory(app, ApplicationStatus.CONTRACT_PENDING, ApplicationStatus.CONTRACT_SIGNED, "Contract signed");

                app.setStatus(ApplicationStatus.APPROVED);
                app.setApprovedAt(LocalDateTime.now());
                applicationRepository.save(app);
                addStatusHistory(app, ApplicationStatus.CONTRACT_SIGNED, ApplicationStatus.APPROVED, "Application approved");

                app.setStatus(ApplicationStatus.ACTIVE);
                applicationRepository.save(app);
                addStatusHistory(app, ApplicationStatus.APPROVED, ApplicationStatus.ACTIVE, "Account is now active");

                emailService.sendStatusUpdateEmail(app.getUser().getEmail(),
                        app.getUser().getFirstName(), app.getReferenceNumber(), "Compte actif");
            }
        });
    }

    // ── Private helpers ──

    private AccountApplication findById(UUID id) {
        return applicationRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Application not found: " + id));
    }

    private void checkOwnership(AccountApplication app) {
        User current = keycloakUserService.getCurrentUser();
        if (!app.getUser().getId().equals(current.getId())) {
            throw new AccessDeniedException("You do not have access to this application");
        }
    }

    private void validateRequiredFields(AccountApplication app) {
        if (app.getDateOfBirth() == null) throw new IllegalStateException("Date of birth is required");
        if (app.getAddressLine1() == null || app.getAddressLine1().isBlank()) throw new IllegalStateException("Address is required");
        if (app.getCity() == null || app.getCity().isBlank()) throw new IllegalStateException("City is required");
        if (app.getPostalCode() == null || app.getPostalCode().isBlank()) throw new IllegalStateException("Postal code is required");
        if (app.getCountry() == null || app.getCountry().isBlank()) throw new IllegalStateException("Country is required");
    }

    private void validateRequiredKycDocuments(AccountApplication app) {
        List<KycDocument> docs = app.getKycDocuments();
        boolean hasIdOrPassport = docs.stream().anyMatch(d ->
                d.getDocumentType() == KycDocumentType.ID_CARD || d.getDocumentType() == KycDocumentType.PASSPORT);
        boolean hasProofOfAddress = docs.stream().anyMatch(d ->
                d.getDocumentType() == KycDocumentType.PROOF_OF_ADDRESS);

        if (!hasIdOrPassport) {
            throw new IllegalStateException("Une pièce d'identité (CNI ou Passeport) est requise");
        }
        if (!hasProofOfAddress) {
            throw new IllegalStateException("Un justificatif de domicile est requis");
        }
    }

    /**
     * Checks if all required KYC documents are uploaded and approved.
     * If so, auto-transitions the application from KYC_PENDING to KYC_VERIFIED.
     */
    private void tryAutoVerifyKyc(AccountApplication app) {
        if (app.getStatus() != ApplicationStatus.KYC_PENDING) return;

        List<KycDocument> docs = app.getKycDocuments();
        boolean hasApprovedId = docs.stream().anyMatch(d ->
                (d.getDocumentType() == KycDocumentType.ID_CARD || d.getDocumentType() == KycDocumentType.PASSPORT)
                        && d.getStatus() == KycDocumentStatus.APPROVED);
        boolean hasApprovedAddress = docs.stream().anyMatch(d ->
                d.getDocumentType() == KycDocumentType.PROOF_OF_ADDRESS
                        && d.getStatus() == KycDocumentStatus.APPROVED);

        if (hasApprovedId && hasApprovedAddress) {
            app.setStatus(ApplicationStatus.KYC_VERIFIED);
            applicationRepository.save(app);
            addStatusHistory(app, ApplicationStatus.KYC_PENDING, ApplicationStatus.KYC_VERIFIED,
                    "KYC documents verified automatically by OCR");
            log.info("Application {} auto-verified KYC (all required docs approved)", app.getReferenceNumber());
        }
    }

    private void applyFields(AccountApplication app, String dob, String phone, String nationality,
                             String addr1, String addr2, String city, String postal, String country,
                             String employment, String employer, String job, java.math.BigDecimal income) {
        if (dob != null && !dob.isBlank()) app.setDateOfBirth(LocalDate.parse(dob));
        if (phone != null && !phone.isBlank()) app.setPhoneNumber(phone);
        if (nationality != null && !nationality.isBlank()) app.setNationality(nationality);
        if (addr1 != null && !addr1.isBlank()) app.setAddressLine1(addr1);
        if (addr2 != null) app.setAddressLine2(addr2);
        if (city != null && !city.isBlank()) app.setCity(city);
        if (postal != null && !postal.isBlank()) app.setPostalCode(postal);
        if (country != null && !country.isBlank()) app.setCountry(country);
        if (employment != null && !employment.isBlank()) app.setEmploymentStatus(EmploymentStatus.valueOf(employment));
        if (employer != null) app.setEmployerName(employer);
        if (job != null) app.setJobTitle(job);
        if (income != null) app.setMonthlyIncome(income);
    }

    private void addStatusHistory(AccountApplication app, ApplicationStatus from, ApplicationStatus to, String comment) {
        ApplicationStatusHistory h = ApplicationStatusHistory.builder()
                .application(app)
                .fromStatus(from != null ? from.name() : null)
                .toStatus(to.name())
                .changedBy(keycloakUserService.getCurrentUser())
                .comment(comment)
                .build();
        statusHistoryRepository.save(h);
    }

    private String generateReferenceNumber() {
        return "ACC-" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
                + "-" + String.format("%04d", new Random().nextInt(10000));
    }

    private String saveKycFile(String fileName, byte[] bytes) {
        try {
            Path uploadPath = Paths.get(KYC_UPLOAD_DIR);
            Files.createDirectories(uploadPath);
            String uniqueName = UUID.randomUUID() + "_" + fileName;
            Path filePath = uploadPath.resolve(uniqueName);
            try (OutputStream os = Files.newOutputStream(filePath)) { os.write(bytes); }
            return filePath.toString();
        } catch (IOException e) {
            throw new RuntimeException("Failed to save KYC file: " + e.getMessage(), e);
        }
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return null;
        return s.length() > maxLen ? s.substring(0, maxLen) + "..." : s;
    }

    // ── Mappers ──

    private AccountTypeResponse toAccountTypeResponse(AccountType t) {
        return AccountTypeResponse.builder().id(t.getId()).code(t.getCode())
                .label(t.getLabel()).description(t.getDescription()).monthlyFee(t.getMonthlyFee()).build();
    }

    private AccountApplicationResponse toResponse(AccountApplication app) {
        return AccountApplicationResponse.builder()
                .id(app.getId())
                .referenceNumber(app.getReferenceNumber())
                .status(app.getStatus())
                .accountType(toAccountTypeResponse(app.getAccountType()))
                .dateOfBirth(app.getDateOfBirth())
                .phoneNumber(app.getPhoneNumber())
                .nationality(app.getNationality())
                .addressLine1(app.getAddressLine1()).addressLine2(app.getAddressLine2())
                .city(app.getCity()).postalCode(app.getPostalCode()).country(app.getCountry())
                .employmentStatus(app.getEmploymentStatus())
                .employerName(app.getEmployerName()).jobTitle(app.getJobTitle())
                .monthlyIncome(app.getMonthlyIncome())
                .contractDocumentId(app.getContractDocument() != null ? app.getContractDocument().getId() : null)
                .kycDocuments(app.getKycDocuments().stream().map(this::toKycResponse).collect(Collectors.toList()))
                .statusHistory(statusHistoryRepository.findByApplicationIdOrderByChangedAtDesc(app.getId())
                        .stream().map(h -> StatusHistoryResponse.builder()
                                .fromStatus(h.getFromStatus()).toStatus(h.getToStatus())
                                .comment(h.getComment()).changedAt(h.getChangedAt()).build())
                        .collect(Collectors.toList()))
                .submittedAt(app.getSubmittedAt()).approvedAt(app.getApprovedAt())
                .createdAt(app.getCreatedAt()).updatedAt(app.getUpdatedAt())
                .build();
    }

    private KycDocumentResponse toKycResponse(KycDocument kyc) {
        return KycDocumentResponse.builder()
                .id(kyc.getId())
                .documentType(kyc.getDocumentType())
                .fileName(kyc.getFileName())
                .status(kyc.getStatus())
                .rejectionReason(kyc.getRejectionReason())
                .createdAt(kyc.getCreatedAt())
                .ocrStatus(kyc.getOcrStatus())
                .ocrMatchScore(kyc.getOcrMatchScore())
                .documentTypeValid(kyc.getDocumentTypeValid())
                .ocrDetails(ocrService.deserializeDetails(kyc.getOcrDetails()))
                .ocrWarnings(ocrService.deserializeWarnings(kyc.getOcrWarnings()))
                .build();
    }
}

