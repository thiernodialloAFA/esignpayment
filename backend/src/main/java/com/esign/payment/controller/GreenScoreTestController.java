package com.esign.payment.controller;

import com.esign.payment.config.TestDataInitializer;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;
import static java.util.Map.entry;

/**
 * Test-profile-only controller that exposes scenario data for the
 * Green Score Analyzer.  The Python script fetches this to obtain
 * correct path-parameter values and request bodies for every endpoint.
 */
@RestController
@RequestMapping("/api/test/green-score")
@Profile("test")
@Hidden  // hide from OpenAPI spec — the analyzer should not measure this endpoint
public class GreenScoreTestController {

    @GetMapping("/scenario")
    public ResponseEntity<Map<String, Object>> getScenario() {
        Map<String, Object> scenario = new LinkedHashMap<>();

        // ── Path params per endpoint pattern ──
        // The analyzer matches the OpenAPI path exactly and uses these values
        // to fill {param} placeholders instead of the default "1".
        Map<String, Map<String, String>> pathParams = new LinkedHashMap<>();

        // Account applications
        pathParams.put("/api/account-applications/{id}",
                Map.of("id", TestDataInitializer.APP_DRAFT_ID));
        pathParams.put("/api/account-applications/{id}/submit",
                Map.of("id", TestDataInitializer.APP_SUBMITTABLE_ID));
        pathParams.put("/api/account-applications/{id}/kyc",
                Map.of("id", TestDataInitializer.APP_FOR_KYC_UPLOAD_ID));
        pathParams.put("/api/account-applications/{id}/kyc/{kycId}",
                Map.of("id", TestDataInitializer.KYC_DELETABLE_APP_ID,
                        "kycId", TestDataInitializer.KYC_DELETABLE_ID));
        pathParams.put("/api/account-applications/{id}/generate-contract",
                Map.of("id", TestDataInitializer.APP_KYC_VERIFIED_ID));
        pathParams.put("/api/account-applications/{id}/regenerate-contract",
                Map.of("id", TestDataInitializer.APP_CONTRACT_PENDING_ID));

        // Documents
        pathParams.put("/api/documents/{id}",
                Map.of("id", TestDataInitializer.DOC_READ_ID));
        pathParams.put("/api/documents/{id}/send",
                Map.of("id", TestDataInitializer.DOC_DRAFT_ID));
        pathParams.put("/api/documents/{id}/resend",
                Map.of("id", TestDataInitializer.DOC_PENDING_ID));
        pathParams.put("/api/documents/{id}/download",
                Map.of("id", TestDataInitializer.DOC_READ_ID));
        pathParams.put("/api/documents/{id}/live-sign/{signerId}",
                Map.of("id", TestDataInitializer.DOC_FOR_LIVE_SIGN_ID,
                        "signerId", TestDataInitializer.SIGNER_FOR_LIVE_SIGN_ID));

        // Signing (public endpoints)
        pathParams.put("/api/sign/verify/{token}",
                Map.of("token", TestDataInitializer.TOKEN_VERIFY));
        pathParams.put("/api/sign/{token}/send-otp",
                Map.of("token", TestDataInitializer.TOKEN_SEND_OTP));
        pathParams.put("/api/sign/{token}/verify-otp",
                Map.of("token", TestDataInitializer.TOKEN_VERIFY_OTP));
        pathParams.put("/api/sign/{token}",
                Map.of("token", TestDataInitializer.TOKEN_SIGN));

        // Payments
        pathParams.put("/api/payments/{id}",
                Map.of("id", TestDataInitializer.PAYMENT_READ_ID));
        pathParams.put("/api/payments/{id}/cancel",
                Map.of("id", TestDataInitializer.PAYMENT_CANCEL_ID));

        scenario.put("pathParams", pathParams);

        // ── Request bodies for POST/PUT endpoints ──
        // Keyed by "method:path"
        Map<String, Object> requestBodies = new LinkedHashMap<>();

        // POST /api/account-applications
        requestBodies.put("post:/api/account-applications", Map.ofEntries(
                entry("accountTypeCode", TestDataInitializer.ACCOUNT_TYPE_CODE),
                entry("dateOfBirth", "1992-03-15"),
                entry("phoneNumber", "+33698765432"),
                entry("nationality", "Française"),
                entry("addressLine1", "456 Avenue des Champs"),
                entry("city", "Lyon"),
                entry("postalCode", "69001"),
                entry("country", "France"),
                entry("employmentStatus", "EMPLOYED"),
                entry("employerName", "TechCorp"),
                entry("jobTitle", "Developer"),
                entry("monthlyIncome", 4200)
        ));

        // PUT /api/account-applications/{id}
        requestBodies.put("put:/api/account-applications/{id}", Map.of(
                "phoneNumber", "+33611111111",
                "city", "Marseille",
                "postalCode", "13001"
        ));

        // POST /api/account-applications/{id}/kyc
        // Small base64-encoded PNG (1x1 pixel)
        String tinyBase64 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==";
        requestBodies.put("post:/api/account-applications/{id}/kyc", Map.of(
                "documentType", "ID_CARD",
                "fileName", "test_id_card.png",
                "contentType", "image/png",
                "fileContent", tinyBase64
        ));

        // POST /api/documents
        requestBodies.put("post:/api/documents", Map.of(
                "title", "Green Score Test Document",
                "description", "Auto-created by Green Score Analyzer",
                "fileName", "green_test.pdf",
                "contentType", "application/pdf",
                "fileContent", "JVBERi0xLjQKMSAwIG9iago8PC9UeXBlL0NhdGFsb2cvUGFnZXMgMiAwIFI+PgplbmRvYmoKdHJhaWxlcgo8PC9Sb290IDEgMCBSPj4K",
                "signers", List.of(Map.of(
                        "email", "green-signer@test.com",
                        "name", "Green Signer",
                        "phone", "+33600000099"
                ))
        ));

        // POST /api/documents/{id}/live-sign/{signerId}
        String signatureDataUrl = "data:image/png;base64," + tinyBase64;
        requestBodies.put("post:/api/documents/{id}/live-sign/{signerId}", Map.of(
                "signatureData", signatureDataUrl
        ));

        // POST /api/sign/{token}/send-otp
        requestBodies.put("post:/api/sign/{token}/send-otp", Map.of(
                "phoneNumber", "+33612345678"
        ));

        // POST /api/sign/{token}/verify-otp
        requestBodies.put("post:/api/sign/{token}/verify-otp", Map.of(
                "otpCode", "123456"
        ));

        // POST /api/sign/{token}
        requestBodies.put("post:/api/sign/{token}", Map.of(
                "signatureData", signatureDataUrl
        ));

        // POST /api/payments
        requestBodies.put("post:/api/payments", Map.of(
                "amount", 25.00,
                "currency", "EUR",
                "description", "Green Score test payment"
        ));

        // POST /api/payments/confirm
        requestBodies.put("post:/api/payments/confirm", Map.of(
                "paymentIntentId", TestDataInitializer.PAYMENT_CONFIRM_INTENT_ID
        ));

        scenario.put("requestBodies", requestBodies);

        // ── Endpoints to exclude (cannot return 200 without special setup) ──
        scenario.put("excludeEndpoints", List.of(
                "post:/api/webhooks/stripe"  // requires Stripe-Signature header
        ));

        return ResponseEntity.ok(scenario);
    }
}
