package com.esign.payment.controller;

import com.esign.payment.dto.request.CreateDocumentRequest;
import com.esign.payment.dto.request.SendOtpRequest;
import com.esign.payment.dto.request.SignDocumentRequest;
import com.esign.payment.dto.request.VerifyOtpRequest;
import com.esign.payment.dto.response.ApiResponse;
import com.esign.payment.dto.response.DocumentResponse;
import com.esign.payment.dto.response.DocumentSignerResponse;
import com.esign.payment.dto.response.OtpResponse;
import com.esign.payment.model.Document;
import com.esign.payment.service.DocumentService;
import com.esign.payment.service.OtpService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Documents", description = "Document upload, e-signature and OTP signing workflows")
public class DocumentController {

    private final DocumentService documentService;
    private final OtpService otpService;

    @PostMapping("/documents")
    public ResponseEntity<ApiResponse<DocumentResponse>> createDocument(
            @Valid @RequestBody CreateDocumentRequest request) {
        DocumentResponse response = documentService.createDocument(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Document created successfully", response));
    }

    @GetMapping("/documents")
    public ResponseEntity<ApiResponse<List<DocumentResponse>>> getMyDocuments() {
        List<DocumentResponse> documents = documentService.getMyDocuments();
        return ResponseEntity.ok(ApiResponse.success(documents));
    }

    @GetMapping("/documents/{id}")
    public ResponseEntity<ApiResponse<DocumentResponse>> getDocument(@PathVariable UUID id) {
        DocumentResponse document = documentService.getDocument(id);
        return ResponseEntity.ok(ApiResponse.success(document));
    }

    @PostMapping("/documents/{id}/send")
    public ResponseEntity<ApiResponse<DocumentResponse>> sendForSignature(@PathVariable UUID id) {
        DocumentResponse document = documentService.sendForSignature(id);
        return ResponseEntity.ok(ApiResponse.success("Document sent for signature", document));
    }

    @PostMapping("/documents/{id}/resend")
    public ResponseEntity<ApiResponse<DocumentResponse>> resendForSignature(@PathVariable UUID id) {
        DocumentResponse document = documentService.resendForSignature(id);
        return ResponseEntity.ok(ApiResponse.success("Signature request resent to pending signers", document));
    }

    @GetMapping("/documents/{id}/download")
    public ResponseEntity<byte[]> downloadDocument(@PathVariable UUID id) {
        Document document = documentService.getDocumentEntity(id);
        byte[] fileContent = documentService.getDocumentFile(id);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(document.getContentType()));
        headers.setContentDispositionFormData("inline", document.getFileName());
        headers.set("Content-Disposition", "inline; filename=\"" + document.getFileName() + "\"");

        return new ResponseEntity<>(fileContent, headers, HttpStatus.OK);
    }


    @DeleteMapping("/documents/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteDocument(@PathVariable UUID id) {
        documentService.deleteDocument(id);
        return ResponseEntity.ok(ApiResponse.success("Document deleted successfully", null));
    }

    /**
     * Live/in-person signing: the document owner signs on behalf of a signer who is physically present.
     * No OTP verification required.
     */
    @PostMapping("/documents/{id}/live-sign/{signerId}")
    public ResponseEntity<ApiResponse<DocumentResponse>> liveSign(
            @PathVariable UUID id,
            @PathVariable UUID signerId,
            @Valid @RequestBody SignDocumentRequest request,
            HttpServletRequest httpRequest) {
        String ipAddress = httpRequest.getRemoteAddr();
        DocumentResponse document = documentService.signDocumentLive(id, signerId, request.getSignatureData(), ipAddress);
        return ResponseEntity.ok(ApiResponse.success("Document signed in person", document));
    }

    @GetMapping("/sign/verify/{token}")
    @Operation(summary = "Verify a signature token (public)")
    @SecurityRequirements
    public ResponseEntity<ApiResponse<DocumentSignerResponse>> verifySignatureToken(@PathVariable String token) {
        DocumentSignerResponse signer = documentService.verifySignatureToken(token);
        return ResponseEntity.ok(ApiResponse.success(signer));
    }

    /**
     * Sends an OTP code via SMS to the signer's phone number for signature verification.
     */
    @PostMapping("/sign/{token}/send-otp")
    @Operation(summary = "Send OTP code via SMS to signer (public)")
    @SecurityRequirements
    public ResponseEntity<ApiResponse<OtpResponse>> sendOtp(
            @PathVariable String token,
            @Valid @RequestBody SendOtpRequest request) {
        otpService.generateAndSendOtp(token, request.getPhoneNumber());
        OtpResponse response = OtpResponse.builder()
                .sent(true)
                .message("OTP code sent to your phone number")
                .build();
        return ResponseEntity.ok(ApiResponse.success("OTP sent successfully", response));
    }

    /**
     * Verifies the OTP code provided by the signer.
     */
    @PostMapping("/sign/{token}/verify-otp")
    @Operation(summary = "Verify OTP code (public)")
    @SecurityRequirements
    public ResponseEntity<ApiResponse<OtpResponse>> verifyOtp(
            @PathVariable String token,
            @Valid @RequestBody VerifyOtpRequest request) {
        boolean verified = otpService.verifyOtp(token, request.getOtpCode());
        OtpResponse response = OtpResponse.builder()
                .verified(verified)
                .message(verified ? "OTP verified successfully" : "Invalid OTP code")
                .build();

        if (verified) {
            return ResponseEntity.ok(ApiResponse.success("OTP verified", response));
        } else {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.<OtpResponse>builder()
                            .success(false)
                            .message("Invalid OTP code")
                            .data(response)
                            .build());
        }
    }

    @PostMapping("/sign/{token}")
    @Operation(summary = "Submit signature for a document (public)")
    @SecurityRequirements
    public ResponseEntity<ApiResponse<DocumentResponse>> signDocument(
            @PathVariable String token,
            @Valid @RequestBody SignDocumentRequest request,
            HttpServletRequest httpRequest) {
        String ipAddress = httpRequest.getRemoteAddr();
        DocumentResponse document = documentService.signDocument(token, request, ipAddress);
        return ResponseEntity.ok(ApiResponse.success("Document signed successfully", document));
    }
}
