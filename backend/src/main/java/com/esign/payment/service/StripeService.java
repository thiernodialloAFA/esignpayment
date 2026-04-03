package com.esign.payment.service;

import com.esign.payment.config.StripeConfig;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
@Slf4j
public class StripeService {

    private final StripeConfig stripeConfig;

    /**
     * Creates a Stripe PaymentIntent with 3D Secure support.
     * The amount is in the smallest currency unit (e.g., cents for EUR).
     */
    public PaymentIntent createPaymentIntent(BigDecimal amount, String currency, String description)
            throws StripeException {

        long amountInCents = amount.multiply(BigDecimal.valueOf(100)).longValue();

        PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                .setAmount(amountInCents)
                .setCurrency(currency.toLowerCase())
                .setDescription(description)
                .addPaymentMethodType("card")
                .setAutomaticPaymentMethods(
                        PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                                .setEnabled(false)
                                .build()
                )
                .build();

        PaymentIntent paymentIntent = PaymentIntent.create(params);
        log.info("Created Stripe PaymentIntent: {}", paymentIntent.getId());
        return paymentIntent;
    }

    /**
     * Retrieves a PaymentIntent by ID to check its status.
     */
    public PaymentIntent retrievePaymentIntent(String paymentIntentId) throws StripeException {
        return PaymentIntent.retrieve(paymentIntentId);
    }

    /**
     * Returns the publishable key for frontend Stripe initialization.
     */
    public String getPublishableKey() {
        return stripeConfig.getPublishableKey();
    }
}
