package com.esign.payment.controller;

import com.esign.payment.dto.request.*;
import com.esign.payment.dto.response.*;
import com.esign.payment.service.AccountApplicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AccountApplicationController {

    private final AccountApplicationService service;

    @GetMapping("/account-types")
    public ResponseEntity<ApiResponse<List<AccountTypeResponse>>> getAccountTypes() {
        return ResponseEntity.ok(ApiResponse.success(service.getActiveAccountTypes()));
    }

    @PostMapping("/account-applications")
    public ResponseEntity<ApiResponse<AccountApplicationResponse>> create(
            @Valid @RequestBody CreateAccountApplicationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Application created", service.createApplication(request)));
    }

    @PutMapping("/account-applications/{id}")
    public ResponseEntity<ApiResponse<AccountApplicationResponse>> update(
            @PathVariable UUID id, @Valid @RequestBody UpdateAccountApplicationRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Application updated", service.updateApplication(id, request)));
    }

    @GetMapping("/account-applications")
    public ResponseEntity<ApiResponse<List<AccountApplicationResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.success(service.getMyApplications()));
    }

    @GetMapping("/account-applications/{id}")
    public ResponseEntity<ApiResponse<AccountApplicationResponse>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(service.getApplication(id)));
    }

    @PostMapping("/account-applications/{id}/submit")
    public ResponseEntity<ApiResponse<AccountApplicationResponse>> submit(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Application submitted", service.submitApplication(id)));
    }

    @PostMapping("/account-applications/{id}/kyc")
    public ResponseEntity<ApiResponse<KycDocumentResponse>> uploadKyc(
            @PathVariable UUID id, @Valid @RequestBody UploadKycDocumentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("KYC document uploaded", service.uploadKycDocument(id, request)));
    }

    @DeleteMapping("/account-applications/{id}/kyc/{kycId}")
    public ResponseEntity<ApiResponse<Void>> deleteKyc(@PathVariable UUID id, @PathVariable UUID kycId) {
        service.deleteKycDocument(id, kycId);
        return ResponseEntity.ok(ApiResponse.success("KYC document deleted", null));
    }

    @PostMapping("/account-applications/{id}/generate-contract")
    public ResponseEntity<ApiResponse<AccountApplicationResponse>> generateContract(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Contract generated", service.generateContract(id)));
    }

    @PostMapping("/account-applications/{id}/regenerate-contract")
    public ResponseEntity<ApiResponse<AccountApplicationResponse>> regenerateContract(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Contract regenerated", service.regenerateContract(id)));
    }

    @DeleteMapping("/account-applications/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        service.deleteApplication(id);
        return ResponseEntity.ok(ApiResponse.success("Application deleted", null));
    }
}

