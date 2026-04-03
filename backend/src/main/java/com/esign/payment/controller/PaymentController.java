package com.esign.payment.controller;

import com.esign.payment.dto.request.ConfirmPaymentRequest;
import com.esign.payment.dto.request.CreatePaymentRequest;
import com.esign.payment.dto.response.ApiResponse;
import com.esign.payment.dto.response.PaymentResponse;
import com.esign.payment.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    /**
     * Creates a new payment and returns a Stripe client secret for card payment with 3D Secure.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<PaymentResponse>> createPayment(
            @Valid @RequestBody CreatePaymentRequest request) {
        PaymentResponse payment = paymentService.createPayment(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Payment initiated. Complete card payment on the client.", payment));
    }

    /**
     * Confirms a payment after 3D Secure authentication on the frontend.
     */
    @PostMapping("/confirm")
    public ResponseEntity<ApiResponse<PaymentResponse>> confirmPayment(
            @Valid @RequestBody ConfirmPaymentRequest request) {
        PaymentResponse payment = paymentService.confirmPayment(request.getPaymentIntentId());
        return ResponseEntity.ok(ApiResponse.success("Payment confirmed", payment));
    }

    /**
     * Returns the Stripe publishable key for frontend initialization.
     */
    @GetMapping("/config")
    public ResponseEntity<ApiResponse<Map<String, String>>> getPaymentConfig() {
        Map<String, String> config = Map.of(
                "publishableKey", paymentService.getStripePublishableKey()
        );
        return ResponseEntity.ok(ApiResponse.success(config));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<PaymentResponse>>> getMyPayments() {
        List<PaymentResponse> payments = paymentService.getMyPayments();
        return ResponseEntity.ok(ApiResponse.success(payments));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PaymentResponse>> getPayment(@PathVariable UUID id) {
        PaymentResponse payment = paymentService.getPayment(id);
        return ResponseEntity.ok(ApiResponse.success(payment));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<PaymentResponse>> cancelPayment(@PathVariable UUID id) {
        PaymentResponse payment = paymentService.cancelPayment(id);
        return ResponseEntity.ok(ApiResponse.success("Payment cancelled", payment));
    }
}
