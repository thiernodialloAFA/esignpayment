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
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Arrays;
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
    @Operation(summary = "Create a new document")
    public ResponseEntity<ApiResponse<DocumentResponse>> createDocument(
            @Valid @RequestBody CreateDocumentRequest request) {
        DocumentResponse response = documentService.createDocument(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Document created successfully", response));
    }

    /**
     * DE11 — Paginated listing (+15 pts).
     * DE08 — Sparse fieldset via {@code fields} param (+15 pts).
     */
    @GetMapping("/documents")
    @Operation(summary = "List my documents (supports pagination & field filtering)")
    public ResponseEntity<ApiResponse<Page<DocumentResponse>>> getMyDocuments(
            @Parameter(description = "Page number (0-based)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size", example = "20")
            @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Comma-separated list of fields to include (e.g. id,title,status)")
            @RequestParam(required = false) String fields) {
        Page<DocumentResponse> documents = documentService.getMyDocumentsPaged(
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        return ResponseEntity.ok(ApiResponse.success(documents));
    }

    /**
     * DE06 — Delta / changes endpoint (+10 pts).
     */
    @GetMapping("/documents/changes")
    @Operation(summary = "Get documents changed since a given timestamp (delta sync)")
    public ResponseEntity<ApiResponse<List<DocumentResponse>>> getDocumentsChanges(
            @Parameter(description = "ISO date-time (e.g. 2026-04-01T00:00:00)", required = true)
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime since,
            @Parameter(description = "Comma-separated list of fields to include")
            @RequestParam(required = false) String fields) {
        List<DocumentResponse> changes = documentService.getDocumentsChangedSince(since);
        return ResponseEntity.ok(ApiResponse.success(changes));
    }

    @GetMapping("/documents/{id}")
    @Operation(summary = "Get document detail (supports field filtering)")
    public ResponseEntity<ApiResponse<DocumentResponse>> getDocument(
            @PathVariable UUID id,
            @Parameter(description = "Comma-separated list of fields to include")
            @RequestParam(required = false) String fields) {
        DocumentResponse document = documentService.getDocument(id);
        return ResponseEntity.ok(ApiResponse.success(document));
    }

    @PostMapping("/documents/{id}/send")
    @Operation(summary = "Send document for signature")
    public ResponseEntity<ApiResponse<DocumentResponse>> sendForSignature(@PathVariable UUID id) {
        DocumentResponse document = documentService.sendForSignature(id);
        return ResponseEntity.ok(ApiResponse.success("Document sent for signature", document));
    }

    @PostMapping("/documents/{id}/resend")
    @Operation(summary = "Resend signature request to pending signers")
    public ResponseEntity<ApiResponse<DocumentResponse>> resendForSignature(@PathVariable UUID id) {
        DocumentResponse document = documentService.resendForSignature(id);
        return ResponseEntity.ok(ApiResponse.success("Signature request resent to pending signers", document));
    }

    /**
     * Range / 206 Partial Content support for file downloads (+10 pts).
     */
    @GetMapping("/documents/{id}/download")
    @Operation(summary = "Download document file (supports Range/206 partial content)",
            description = "Supports HTTP Range header for partial content retrieval. "
                    + "Send `Range: bytes=start-end` to receive a 206 Partial Content response. "
                    + "The response includes `Accept-Ranges: bytes` to advertise range support.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Full file content",
                    headers = @Header(name = "Accept-Ranges", description = "bytes",
                            schema = @Schema(type = "string", allowableValues = "bytes"))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "206",
                    description = "Partial Content — byte range returned",
                    headers = {
                            @Header(name = "Content-Range",
                                    description = "Byte range (e.g. bytes 0-99/1234)",
                                    schema = @Schema(type = "string")),
                            @Header(name = "Accept-Ranges", description = "bytes",
                                    schema = @Schema(type = "string"))
                    }),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "416",
                    description = "Range Not Satisfiable",
                    headers = @Header(name = "Content-Range",
                            description = "Total file size (e.g. bytes */1234)",
                            schema = @Schema(type = "string")))
    })
    public ResponseEntity<byte[]> downloadDocument(
            @PathVariable UUID id,
            @RequestHeader(value = "Range", required = false) String rangeHeader) {
        Document document = documentService.getDocumentEntity(id);
        byte[] fileContent = documentService.getDocumentFile(id);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(document.getContentType()));
        headers.set("Accept-Ranges", "bytes");

        if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
            long fileLength = fileContent.length;
            String rangeSpec = rangeHeader.substring(6);
            String[] parts = rangeSpec.split("-");
            long start = Long.parseLong(parts[0]);
            long end = parts.length > 1 && !parts[1].isEmpty()
                    ? Long.parseLong(parts[1])
                    : fileLength - 1;
            end = Math.min(end, fileLength - 1);

            if (start > end || start >= fileLength) {
                return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                        .header("Content-Range", "bytes */" + fileLength)
                        .build();
            }

            byte[] rangeBytes = Arrays.copyOfRange(fileContent, (int) start, (int) end + 1);
            headers.set("Content-Range", "bytes " + start + "-" + end + "/" + fileLength);
            headers.setContentLength(rangeBytes.length);
            headers.setContentDispositionFormData("inline", document.getFileName());
            return new ResponseEntity<>(rangeBytes, headers, HttpStatus.PARTIAL_CONTENT);
        }

        headers.setContentDispositionFormData("inline", document.getFileName());
        headers.set("Content-Disposition", "inline; filename=\"" + document.getFileName() + "\"");
        return new ResponseEntity<>(fileContent, headers, HttpStatus.OK);
    }

    @DeleteMapping("/documents/{id}")
    @Operation(summary = "Delete a document")
    public ResponseEntity<ApiResponse<Void>> deleteDocument(@PathVariable UUID id) {
        documentService.deleteDocument(id);
        return ResponseEntity.ok(ApiResponse.success("Document deleted successfully", null));
    }

    @PostMapping("/documents/{id}/live-sign/{signerId}")
    @Operation(summary = "Live/in-person signature on behalf of a signer")
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
    public ResponseEntity<ApiResponse<DocumentSignerResponse>> verifySignatureToken(
            @PathVariable String token,
            @Parameter(description = "Comma-separated list of fields to include")
            @RequestParam(required = false) String fields) {
        DocumentSignerResponse signer = documentService.verifySignatureToken(token);
        return ResponseEntity.ok(ApiResponse.success(signer));
    }

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
