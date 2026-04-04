package com.esign.payment.service;

import com.esign.payment.dto.request.CreateDocumentRequest;
import com.esign.payment.dto.request.SignDocumentRequest;
import com.esign.payment.dto.response.DocumentResponse;
import com.esign.payment.dto.response.DocumentSignerResponse;
import com.esign.payment.model.*;
import com.esign.payment.model.enums.DocumentStatus;
import com.esign.payment.model.enums.SignerStatus;
import com.esign.payment.repository.*;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Service
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentSignerRepository documentSignerRepository;
    private final SignatureRepository signatureRepository;
    private final UserRepository userRepository;
    private final KeycloakUserService keycloakUserService;
    private final OtpService otpService;
    private final SmsService smsService;
    private final AccountApplicationService accountApplicationService;
    private final ContractPdfService contractPdfService;

    @org.springframework.beans.factory.annotation.Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    private static final String UPLOAD_DIR = "uploads/documents";

    private User currentUser() {
        return keycloakUserService.getCurrentUser();
    }

    @Transactional
    public DocumentResponse createDocument(CreateDocumentRequest request) {
        User owner = currentUser();

        String filePath = saveFile(request.getFileName(), request.getFileContent());

        Document document = Document.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .fileName(request.getFileName())
                .contentType(request.getContentType())
                .filePath(filePath)
                .status(DocumentStatus.DRAFT)
                .owner(owner)
                .build();

        document = documentRepository.save(document);

        if (request.getSigners() != null && !request.getSigners().isEmpty()) {
            Document finalDocument = document;
            List<DocumentSigner> signers = request.getSigners().stream()
                    .map(s -> DocumentSigner.builder()
                            .document(finalDocument)
                            .email(s.getEmail())
                            .name(s.getName())
                            .phone(s.getPhone())
                            .status(SignerStatus.PENDING)
                            .signatureToken(UUID.randomUUID().toString())
                            .build())
                    .collect(Collectors.toList());
            documentSignerRepository.saveAll(signers);
            document.setSigners(signers);
            document.setStatus(DocumentStatus.PENDING_SIGNATURE);
            document = documentRepository.save(document);
        }

        return toDocumentResponse(document);
    }

    @Transactional(readOnly = true)
    public List<DocumentResponse> getMyDocuments() {
        User owner = currentUser();
        return documentRepository.findByOwnerIdOrderByCreatedAtDesc(owner.getId())
                .stream()
                .map(this::toDocumentResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Page<DocumentResponse> getMyDocumentsPaged(Pageable pageable) {
        User owner = currentUser();
        return documentRepository.findByOwnerId(owner.getId(), pageable)
                .map(this::toDocumentResponse);
    }

    @Transactional(readOnly = true)
    public List<DocumentResponse> getDocumentsChangedSince(LocalDateTime since) {
        User owner = currentUser();
        return documentRepository.findByOwnerIdAndUpdatedAtAfterOrderByUpdatedAtDesc(owner.getId(), since)
                .stream()
                .map(this::toDocumentResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public DocumentResponse getDocument(UUID id) {
        Document document = findDocumentById(id);
        checkOwnership(document);
        return toDocumentResponse(document);
    }

    @Transactional
    public DocumentResponse sendForSignature(UUID documentId) {
        Document document = findDocumentById(documentId);
        checkOwnership(document);

        if (document.getSigners().isEmpty()) {
            throw new IllegalStateException("Document has no signers");
        }

        document.setStatus(DocumentStatus.PENDING_SIGNATURE);
        document = documentRepository.save(document);

        // Send signing link via SMS to each signer with a phone number
        sendSigningNotifications(document, document.getSigners());

        return toDocumentResponse(document);
    }

    @Transactional
    public DocumentResponse resendForSignature(UUID documentId) {
        Document document = findDocumentById(documentId);
        checkOwnership(document);

        if (document.getStatus() == DocumentStatus.COMPLETED) {
            throw new IllegalStateException("Document is already fully signed");
        }
        if (document.getStatus() == DocumentStatus.CANCELLED) {
            throw new IllegalStateException("Document has been cancelled");
        }

        // Only resend to signers who have not signed yet
        List<DocumentSigner> pendingSigners = document.getSigners().stream()
                .filter(s -> s.getStatus() != SignerStatus.SIGNED)
                .collect(Collectors.toList());

        if (pendingSigners.isEmpty()) {
            throw new IllegalStateException("All signers have already signed");
        }

        // Reset OTP state for pending signers so they can re-verify
        for (DocumentSigner signer : pendingSigners) {
            signer.setOtpVerified(false);
            signer.setOtpCode(null);
            signer.setOtpAttempts(0);
            signer.setSignatureToken(UUID.randomUUID().toString());
            documentSignerRepository.save(signer);
        }

        if (document.getStatus() == DocumentStatus.DRAFT) {
            document.setStatus(DocumentStatus.PENDING_SIGNATURE);
            document = documentRepository.save(document);
        }

        sendSigningNotifications(document, pendingSigners);

        return toDocumentResponse(document);
    }

    private void sendSigningNotifications(Document document, List<DocumentSigner> signers) {
        for (DocumentSigner signer : signers) {
            if (signer.getPhone() != null && !signer.getPhone().isBlank()) {
                String signingUrl = frontendUrl + "/sign/" + signer.getSignatureToken();
                try {
                    smsService.sendSigningLink(
                            signer.getPhone(),
                            signer.getName(),
                            document.getTitle(),
                            signingUrl
                    );
                } catch (Exception e) {
                    // Log but don't fail the whole operation if one SMS fails
                    System.err.println("Failed to send SMS to " + signer.getPhone() + ": " + e.getMessage());
                }
            }
        }
    }

    @Transactional
    public DocumentResponse signDocument(String token, SignDocumentRequest request, String ipAddress) {
        DocumentSigner signer = documentSignerRepository.findBySignatureToken(token)
                .orElseThrow(() -> new EntityNotFoundException("Invalid or expired signature token"));

        if (signer.getStatus() == SignerStatus.SIGNED) {
            throw new IllegalStateException("Document already signed by this signer");
        }

        // Require OTP verification before signing
        if (!signer.isOtpVerified()) {
            throw new IllegalStateException("SMS verification is required before signing. Please verify your OTP code first.");
        }

        Document document = signer.getDocument();
        if (document.getStatus() == DocumentStatus.CANCELLED) {
            throw new IllegalStateException("Document has been cancelled");
        }

        Signature signature = Signature.builder()
                .document(document)
                .signer(signer)
                .signatureData(request.getSignatureData())
                .ipAddress(ipAddress)
                .build();
        signatureRepository.save(signature);

        signer.setStatus(SignerStatus.SIGNED);
        signer.setSignedAt(java.time.LocalDateTime.now());
        documentSignerRepository.save(signer);

        boolean allSigned = document.getSigners().stream()
                .allMatch(s -> s.getStatus() == SignerStatus.SIGNED);
        if (allSigned) {
            document.setStatus(DocumentStatus.COMPLETED);
            // Generate the signed PDF with embedded signatures
            generateSignedVersion(document);
            // Notify account application workflow if this is a contract
            accountApplicationService.onContractSigned(document.getId());
        } else {
            document.setStatus(DocumentStatus.PARTIALLY_SIGNED);
        }
        documentRepository.save(document);

        return toDocumentResponse(document);
    }

    @Transactional(readOnly = true)
    public byte[] getDocumentFile(UUID id) {
        Document document = findDocumentById(id);
        checkOwnership(document);
        try {
            Path path = Paths.get(document.getFilePath());
            if (!Files.exists(path)) {
                throw new EntityNotFoundException("File not found on disk for document: " + id);
            }
            return Files.readAllBytes(path);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read file: " + e.getMessage(), e);
        }
    }

    @Transactional(readOnly = true)
    public Document getDocumentEntity(UUID id) {
        Document document = findDocumentById(id);
        checkOwnership(document);
        return document;
    }

    /**
     * Live/in-person signing: the document owner initiates a signature for a signer
     * who is physically present. No OTP verification required.
     */
    @Transactional
    public DocumentResponse signDocumentLive(UUID documentId, UUID signerId, String signatureData, String ipAddress) {
        Document document = findDocumentById(documentId);
        checkOwnership(document);

        DocumentSigner signer = document.getSigners().stream()
                .filter(s -> s.getId().equals(signerId))
                .findFirst()
                .orElseThrow(() -> new EntityNotFoundException("Signer not found on this document"));

        if (signer.getStatus() == SignerStatus.SIGNED) {
            throw new IllegalStateException("This signer has already signed the document");
        }

        if (document.getStatus() == DocumentStatus.CANCELLED) {
            throw new IllegalStateException("Document has been cancelled");
        }

        // Save signature
        Signature signature = Signature.builder()
                .document(document)
                .signer(signer)
                .signatureData(signatureData)
                .ipAddress(ipAddress)
                .build();
        signatureRepository.save(signature);

        // Mark signer as signed
        signer.setOtpVerified(true);
        signer.setStatus(SignerStatus.SIGNED);
        signer.setSignedAt(java.time.LocalDateTime.now());
        documentSignerRepository.save(signer);

        // Check if all signed
        boolean allSigned = document.getSigners().stream()
                .allMatch(s -> s.getStatus() == SignerStatus.SIGNED);
        if (allSigned) {
            document.setStatus(DocumentStatus.COMPLETED);
            // Generate the signed PDF with embedded signatures
            generateSignedVersion(document);
            accountApplicationService.onContractSigned(document.getId());
        } else {
            document.setStatus(DocumentStatus.PARTIALLY_SIGNED);
        }
        documentRepository.save(document);

        return toDocumentResponse(document);
    }

    @Transactional
    public void deleteDocument(UUID id) {
        Document document = findDocumentById(id);
        checkOwnership(document);
        documentRepository.delete(document);
    }

    /**
     * Regenerates the contract PDF with signatures embedded, replacing the original.
     */
    private void generateSignedVersion(Document document) {
        try {
            contractPdfService.regenerateWithSignatures(document);
        } catch (Exception e) {
            System.err.println("Failed to regenerate PDF with signatures: " + e.getMessage());
        }
    }

    public DocumentSignerResponse verifySignatureToken(String token) {
        DocumentSigner signer = documentSignerRepository.findBySignatureToken(token)
                .orElseThrow(() -> new EntityNotFoundException("Invalid or expired signature token"));
        return toSignerResponse(signer);
    }

    private Document findDocumentById(UUID id) {
        return documentRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Document not found with id: " + id));
    }

    private void checkOwnership(Document document) {
        User currentUser = currentUser();
        if (!document.getOwner().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("You do not have access to this document");
        }
    }

    private String saveFile(String fileName, String base64Content) {
        try {
            Path uploadPath = Paths.get(UPLOAD_DIR);
            Files.createDirectories(uploadPath);

            String uniqueFileName = UUID.randomUUID() + "_" + fileName;
            Path filePath = uploadPath.resolve(uniqueFileName);

            byte[] fileBytes = Base64.getDecoder().decode(base64Content);
            try (OutputStream os = Files.newOutputStream(filePath)) {
                os.write(fileBytes);
            }

            return filePath.toString();
        } catch (IOException e) {
            throw new RuntimeException("Failed to save file: " + e.getMessage(), e);
        }
    }

    private DocumentResponse toDocumentResponse(Document document) {
        return DocumentResponse.builder()
                .id(document.getId())
                .title(document.getTitle())
                .description(document.getDescription())
                .fileName(document.getFileName())
                .contentType(document.getContentType())
                .status(document.getStatus())
                .owner(toOwnerSummary(document.getOwner()))
                .signers(document.getSigners().stream()
                        .map(this::toSignerResponse)
                        .collect(Collectors.toList()))
                .createdAt(document.getCreatedAt())
                .updatedAt(document.getUpdatedAt())
                .build();
    }

    private com.esign.payment.dto.response.UserResponse toOwnerSummary(User owner) {
        return com.esign.payment.dto.response.UserResponse.builder()
                .id(owner.getId())
                .email(owner.getEmail())
                .firstName(owner.getFirstName())
                .lastName(owner.getLastName())
                .role(owner.getRole())
                .createdAt(owner.getCreatedAt())
                .build();
    }

    private DocumentSignerResponse toSignerResponse(DocumentSigner signer) {
        return DocumentSignerResponse.builder()
                .id(signer.getId())
                .email(signer.getEmail())
                .name(signer.getName())
                .status(signer.getStatus())
                .otpVerified(signer.isOtpVerified())
                .signedAt(signer.getSignedAt())
                .createdAt(signer.getCreatedAt())
                .build();
    }
}
