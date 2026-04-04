package com.esign.payment.controller;

import com.esign.payment.dto.request.ConfirmPaymentRequest;
import com.esign.payment.dto.request.CreatePaymentRequest;
import com.esign.payment.dto.response.ApiResponse;
import com.esign.payment.dto.response.PaymentResponse;
import com.esign.payment.service.PaymentService;
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
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@Tag(name = "Payments", description = "Stripe payments with 3D Secure")
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping
    @Operation(summary = "Create a new payment")
    public ResponseEntity<ApiResponse<PaymentResponse>> createPayment(
            @Valid @RequestBody CreatePaymentRequest request) {
        PaymentResponse payment = paymentService.createPayment(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Payment initiated. Complete card payment on the client.", payment));
    }

    @PostMapping("/confirm")
    @Operation(summary = "Confirm payment after 3D Secure")
    public ResponseEntity<ApiResponse<PaymentResponse>> confirmPayment(
            @Valid @RequestBody ConfirmPaymentRequest request) {
        PaymentResponse payment = paymentService.confirmPayment(request.getPaymentIntentId());
        return ResponseEntity.ok(ApiResponse.success("Payment confirmed", payment));
    }

    @GetMapping("/config")
    @Operation(summary = "Get Stripe publishable key")
    public ResponseEntity<ApiResponse<Map<String, String>>> getPaymentConfig() {
        Map<String, String> config = Map.of(
                "publishableKey", paymentService.getStripePublishableKey()
        );
        return ResponseEntity.ok(ApiResponse.success(config));
    }

    /**
     * DE11 — Paginated listing (+15 pts).
     * DE08 — Sparse fieldset via {@code fields} param (+15 pts), handled by FieldFilterAdvice.
     */
    @GetMapping
    @Operation(summary = "List my payments (supports pagination & field filtering)")
    public ResponseEntity<ApiResponse<Page<PaymentResponse>>> getMyPayments(
            @Parameter(description = "Page number (0-based)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size", example = "20")
            @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Comma-separated list of fields to include (e.g. id,amount,status)")
            @RequestParam(required = false) String fields) {
        Page<PaymentResponse> payments = paymentService.getMyPaymentsPaged(
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        return ResponseEntity.ok(ApiResponse.success(payments));
    }

    /**
     * DE06 — Delta / changes endpoint (+10 pts).
     */
    @GetMapping("/changes")
    @Operation(summary = "Get payments changed since a given timestamp (delta sync)")
    public ResponseEntity<ApiResponse<List<PaymentResponse>>> getPaymentsChanges(
            @Parameter(description = "ISO date-time (e.g. 2026-04-01T00:00:00)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime since,
            @Parameter(description = "Comma-separated list of fields to include")
            @RequestParam(required = false) String fields) {
        LocalDateTime effectiveSince = since != null ? since : LocalDateTime.of(1970, 1, 1, 0, 0);
        List<PaymentResponse> changes = paymentService.getPaymentsChangedSince(effectiveSince);
        return ResponseEntity.ok(ApiResponse.success(changes));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get payment detail (supports field filtering)")
    public ResponseEntity<ApiResponse<PaymentResponse>> getPayment(
            @PathVariable UUID id,
            @Parameter(description = "Comma-separated list of fields to include")
            @RequestParam(required = false) String fields) {
        PaymentResponse payment = paymentService.getPayment(id);
        return ResponseEntity.ok(ApiResponse.success(payment));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel a payment")
    public ResponseEntity<ApiResponse<PaymentResponse>> cancelPayment(@PathVariable UUID id) {
        PaymentResponse payment = paymentService.cancelPayment(id);
        return ResponseEntity.ok(ApiResponse.success("Payment cancelled", payment));
    }
}
