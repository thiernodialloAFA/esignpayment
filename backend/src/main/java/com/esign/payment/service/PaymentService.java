package com.esign.payment.service;

import com.esign.payment.dto.request.CreatePaymentRequest;
import com.esign.payment.dto.response.PaymentResponse;
import com.esign.payment.model.Payment;
import com.esign.payment.model.User;
import com.esign.payment.model.enums.PaymentStatus;
import com.esign.payment.repository.PaymentRepository;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final KeycloakUserService keycloakUserService;
    private final StripeService stripeService;

    private User currentUser() {
        return keycloakUserService.getCurrentUser();
    }

    /**
     * Creates a payment with a Stripe PaymentIntent for credit card payment with 3D Secure.
     * Returns the clientSecret needed by the frontend to complete the payment via Stripe Elements.
     */
    @Transactional
    public PaymentResponse createPayment(CreatePaymentRequest request) {
        User user = currentUser();

        try {
            PaymentIntent paymentIntent = stripeService.createPaymentIntent(
                    request.getAmount(),
                    request.getCurrency(),
                    request.getDescription()
            );

            Payment payment = Payment.builder()
                    .user(user)
                    .amount(request.getAmount())
                    .currency(request.getCurrency().toUpperCase())
                    .description(request.getDescription())
                    .status(PaymentStatus.PENDING)
                    .stripePaymentIntentId(paymentIntent.getId())
                    .providerReference(paymentIntent.getId())
                    .build();

            payment = paymentRepository.save(payment);

            return toPaymentResponse(payment, paymentIntent.getClientSecret());
        } catch (StripeException e) {
            log.error("Failed to create Stripe PaymentIntent: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to initiate payment: " + e.getMessage(), e);
        }
    }

    /**
     * Confirms the payment status after 3D Secure authentication on the frontend.
     * This retrieves the PaymentIntent from Stripe and updates the local payment status.
     */
    @Transactional
    public PaymentResponse confirmPayment(String paymentIntentId) {
        Payment payment = paymentRepository.findByStripePaymentIntentId(paymentIntentId)
                .orElseThrow(() -> new EntityNotFoundException("Payment not found for intent: " + paymentIntentId));

        User user = currentUser();
        if (!payment.getUser().getId().equals(user.getId())) {
            throw new AccessDeniedException("You do not have access to this payment");
        }

        try {
            PaymentIntent paymentIntent = stripeService.retrievePaymentIntent(paymentIntentId);
            PaymentStatus newStatus = mapStripeStatus(paymentIntent.getStatus());
            payment.setStatus(newStatus);
            payment = paymentRepository.save(payment);

            log.info("Payment {} confirmed with status: {}", payment.getId(), newStatus);
            return toPaymentResponse(payment);
        } catch (StripeException e) {
            log.error("Failed to confirm payment: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to confirm payment: " + e.getMessage(), e);
        }
    }

    /**
     * Updates payment status from a Stripe webhook event.
     */
    @Transactional
    public void updatePaymentFromWebhook(String paymentIntentId, String stripeStatus) {
        paymentRepository.findByStripePaymentIntentId(paymentIntentId)
                .ifPresent(payment -> {
                    PaymentStatus newStatus = mapStripeStatus(stripeStatus);
                    payment.setStatus(newStatus);
                    paymentRepository.save(payment);
                    log.info("Webhook updated payment {} to status: {}", payment.getId(), newStatus);
                });
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> getMyPayments() {
        User user = currentUser();
        return paymentRepository.findByUserIdOrderByCreatedAtDesc(user.getId())
                .stream()
                .map(this::toPaymentResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Page<PaymentResponse> getMyPaymentsPaged(Pageable pageable) {
        User user = currentUser();
        return paymentRepository.findByUserId(user.getId(), pageable)
                .map(this::toPaymentResponse);
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> getPaymentsChangedSince(LocalDateTime since) {
        User user = currentUser();
        return paymentRepository.findByUserIdAndUpdatedAtAfterOrderByUpdatedAtDesc(user.getId(), since)
                .stream()
                .map(this::toPaymentResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPayment(UUID id) {
        User user = currentUser();
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Payment not found with id: " + id));

        if (!payment.getUser().getId().equals(user.getId())) {
            throw new AccessDeniedException("You do not have access to this payment");
        }

        return toPaymentResponse(payment);
    }

    @Transactional
    public PaymentResponse cancelPayment(UUID id) {
        User user = currentUser();
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Payment not found with id: " + id));

        if (!payment.getUser().getId().equals(user.getId())) {
            throw new AccessDeniedException("You do not have access to this payment");
        }

        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new IllegalStateException("Only pending payments can be cancelled");
        }

        payment.setStatus(PaymentStatus.CANCELLED);
        payment = paymentRepository.save(payment);
        return toPaymentResponse(payment);
    }

    /**
     * Returns the Stripe publishable key for the frontend.
     */
    public String getStripePublishableKey() {
        return stripeService.getPublishableKey();
    }

    private PaymentStatus mapStripeStatus(String stripeStatus) {
        return switch (stripeStatus) {
            case "requires_payment_method", "requires_confirmation", "requires_action" -> PaymentStatus.PENDING;
            case "processing" -> PaymentStatus.PROCESSING;
            case "succeeded" -> PaymentStatus.SUCCEEDED;
            case "canceled" -> PaymentStatus.CANCELLED;
            default -> PaymentStatus.FAILED;
        };
    }

    private PaymentResponse toPaymentResponse(Payment payment) {
        return toPaymentResponse(payment, null);
    }

    private PaymentResponse toPaymentResponse(Payment payment, String clientSecret) {
        return PaymentResponse.builder()
                .id(payment.getId())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .status(payment.getStatus())
                .description(payment.getDescription())
                .providerReference(payment.getProviderReference())
                .clientSecret(clientSecret)
                .stripePublishableKey(clientSecret != null ? stripeService.getPublishableKey() : null)
                .createdAt(payment.getCreatedAt())
                .updatedAt(payment.getUpdatedAt())
                .build();
    }
}
