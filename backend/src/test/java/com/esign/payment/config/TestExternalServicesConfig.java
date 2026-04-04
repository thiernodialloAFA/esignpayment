package com.esign.payment.config;

import org.springframework.boot.test.context.TestConfiguration;

/**
 * Marker configuration for external service stubs during testing.
 * <p>
 * TwilioConfig now skips Twilio.init() with placeholder credentials.
 * StripeConfig uses placeholder keys (no PostConstruct impact).
 * SmsService calls will fail at runtime if actually invoked —
 * this is expected; tests that need SMS should mock SmsService.
 * </p>
 */
@TestConfiguration
public class TestExternalServicesConfig {
    // No additional beans needed — external configs handle test mode gracefully
}

