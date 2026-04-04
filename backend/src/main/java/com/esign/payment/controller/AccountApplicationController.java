package com.esign.payment.controller;

import com.esign.payment.dto.request.*;
import com.esign.payment.dto.response.*;
import com.esign.payment.service.AccountApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Account Applications", description = "Account opening, KYC upload, OCR verification and contract generation")
public class AccountApplicationController {

    private final AccountApplicationService service;

    @GetMapping("/account-types")
    @Operation(summary = "List available account types (supports field filtering)")
    public ResponseEntity<ApiResponse<List<AccountTypeResponse>>> getAccountTypes(
            @Parameter(description = "Comma-separated list of fields to include")
            @RequestParam(required = false) String fields) {
        return ResponseEntity.ok(ApiResponse.success(service.getActiveAccountTypes()));
    }

    @PostMapping("/account-applications")
    @Operation(summary = "Create a new account application")
    public ResponseEntity<ApiResponse<AccountApplicationResponse>> create(
            @Valid @RequestBody CreateAccountApplicationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Application created", service.createApplication(request)));
    }

    @PutMapping("/account-applications/{id}")
    @Operation(summary = "Update an account application")
    public ResponseEntity<ApiResponse<AccountApplicationResponse>> update(
            @PathVariable UUID id, @Valid @RequestBody UpdateAccountApplicationRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Application updated", service.updateApplication(id, request)));
    }

    /**
     * DE11 — Paginated listing (+15 pts).
     * DE08 — Sparse fieldset via {@code fields} param (+15 pts).
     */
    @GetMapping("/account-applications")
    @Operation(summary = "List my account applications (supports pagination & field filtering)")
    public ResponseEntity<ApiResponse<Page<AccountApplicationResponse>>> list(
            @Parameter(description = "Page number (0-based)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size", example = "20")
            @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Comma-separated list of fields to include (e.g. id,status,referenceNumber)")
            @RequestParam(required = false) String fields) {
        Page<AccountApplicationResponse> apps = service.getMyApplicationsPaged(
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        return ResponseEntity.ok(ApiResponse.success(apps));
    }

    /**
     * DE06 — Delta / changes endpoint (+10 pts).
     */
    @GetMapping("/account-applications/changes")
    @Operation(summary = "Get applications changed since a given timestamp (delta sync)")
    public ResponseEntity<ApiResponse<List<AccountApplicationResponse>>> getChanges(
            @Parameter(description = "ISO date-time (e.g. 2026-04-01T00:00:00)", required = true)
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime since,
            @Parameter(description = "Comma-separated list of fields to include")
            @RequestParam(required = false) String fields) {
        List<AccountApplicationResponse> changes = service.getApplicationsChangedSince(since);
        return ResponseEntity.ok(ApiResponse.success(changes));
    }

    @GetMapping("/account-applications/{id}")
    @Operation(summary = "Get account application detail (supports field filtering)")
    public ResponseEntity<ApiResponse<AccountApplicationResponse>> get(
            @PathVariable UUID id,
            @Parameter(description = "Comma-separated list of fields to include")
            @RequestParam(required = false) String fields) {
        return ResponseEntity.ok(ApiResponse.success(service.getApplication(id)));
    }

    @PostMapping("/account-applications/{id}/submit")
    @Operation(summary = "Submit an account application for review")
    public ResponseEntity<ApiResponse<AccountApplicationResponse>> submit(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Application submitted", service.submitApplication(id)));
    }

    @PostMapping("/account-applications/{id}/kyc")
    @Operation(summary = "Upload a KYC document (base64)")
    public ResponseEntity<ApiResponse<KycDocumentResponse>> uploadKyc(
            @PathVariable UUID id, @Valid @RequestBody UploadKycDocumentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("KYC document uploaded", service.uploadKycDocument(id, request)));
    }

    @DeleteMapping("/account-applications/{id}/kyc/{kycId}")
    @Operation(summary = "Delete a KYC document")
    public ResponseEntity<ApiResponse<Void>> deleteKyc(@PathVariable UUID id, @PathVariable UUID kycId) {
        service.deleteKycDocument(id, kycId);
        return ResponseEntity.ok(ApiResponse.success("KYC document deleted", null));
    }

    @PostMapping("/account-applications/{id}/generate-contract")
    @Operation(summary = "Generate contract PDF for the application")
    public ResponseEntity<ApiResponse<AccountApplicationResponse>> generateContract(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Contract generated", service.generateContract(id)));
    }

    @PostMapping("/account-applications/{id}/regenerate-contract")
    @Operation(summary = "Regenerate contract PDF")
    public ResponseEntity<ApiResponse<AccountApplicationResponse>> regenerateContract(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Contract regenerated", service.regenerateContract(id)));
    }

    @DeleteMapping("/account-applications/{id}")
    @Operation(summary = "Delete an account application")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        service.deleteApplication(id);
        return ResponseEntity.ok(ApiResponse.success("Application deleted", null));
    }
}

