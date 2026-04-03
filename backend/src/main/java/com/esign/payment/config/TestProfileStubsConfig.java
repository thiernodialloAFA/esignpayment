package com.esign.payment.config;

import com.esign.payment.service.SmsService;
import com.esign.payment.service.StripeService;
import com.stripe.model.PaymentIntent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessagePreparator;

import jakarta.mail.internet.MimeMessage;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Stubs all outbound calls (Stripe, Twilio SMS, Email) when running with profile "test".
 * <p>
 * Every external service returns a successful response without making any real network call.
 * </p>
 */
@Configuration
@Profile("test")
@Slf4j
public class TestProfileStubsConfig {

    // ── Stripe Config stub (replaces the @Profile("!test") StripeConfig) ──

    @Bean
    @Primary
    public StripeConfig stripeConfig() {
        log.info("[TEST STUB] StripeConfig — using placeholder keys, no Stripe.apiKey set");
        return new StripeConfig() {
            @Override public String getApiKey()          { return "sk_test_placeholder"; }
            @Override public String getWebhookSecret()   { return "whsec_placeholder"; }
            @Override public String getPublishableKey()   { return "pk_test_placeholder"; }
        };
    }

    // ── Twilio Config stub (replaces the @Profile("!test") TwilioConfig) ──

    @Bean
    @Primary
    public TwilioConfig twilioConfig() {
        log.info("[TEST STUB] TwilioConfig — using placeholder values, no Twilio.init() called");
        return new TwilioConfig() {
            @Override public String getAccountSid()  { return "AC_placeholder"; }
            @Override public String getAuthToken()   { return "placeholder"; }
            @Override public String getPhoneNumber() { return "+15005550006"; }
        };
    }

    // ── Stripe Service stub ──

    @Bean
    @Primary
    public StripeService stripeService() {
        log.info("[TEST STUB] StripeService — all Stripe calls return mock PaymentIntents");
        return new StripeService(stripeConfig()) {

            @Override
            public PaymentIntent createPaymentIntent(BigDecimal amount, String currency, String description) {
                String id = "pi_test_" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
                String clientSecret = id + "_secret_" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
                long cents = amount.multiply(BigDecimal.valueOf(100)).longValue();
                log.info("[TEST STUB] Stripe createPaymentIntent — amount={} {} ({}c), id={}",
                        amount, currency, cents, id);
                return buildStubPaymentIntent(id, clientSecret, "requires_action");
            }

            @Override
            public PaymentIntent retrievePaymentIntent(String paymentIntentId) {
                log.info("[TEST STUB] Stripe retrievePaymentIntent — id={}, returning status=succeeded", paymentIntentId);
                return buildStubPaymentIntent(paymentIntentId, paymentIntentId + "_secret_stub", "succeeded");
            }

            @Override
            public String getPublishableKey() {
                return "pk_test_placeholder";
            }
        };
    }

    // ── SMS Service stub ──

    @Bean
    @Primary
    public SmsService smsService() {
        log.info("[TEST STUB] SmsService — all SMS calls are no-ops");
        return new SmsService(twilioConfig()) {

            @Override
            public void sendSms(String toPhoneNumber, String messageBody) {
                log.info("[TEST STUB] SMS to {} : {}", toPhoneNumber, messageBody);
            }

            @Override
            public void sendOtpCode(String toPhoneNumber, String otpCode, String signerName) {
                log.info("[TEST STUB] OTP code {} sent to {} for signer {}", otpCode, toPhoneNumber, signerName);
            }

            @Override
            public void sendSigningLink(String toPhoneNumber, String signerName, String documentTitle, String signingUrl) {
                log.info("[TEST STUB] Signing link sent to {} : {}", toPhoneNumber, signingUrl);
            }
        };
    }

    // ── JavaMailSender no-op (EmailService uses this — all sends become no-ops) ──

    @Bean
    @Primary
    public JavaMailSender javaMailSender() {
        log.info("[TEST STUB] JavaMailSender — all email sends are no-ops");
        return new NoOpJavaMailSender();
    }

    /**
     * A no-op implementation of JavaMailSender that logs instead of sending emails.
     */
    static class NoOpJavaMailSender implements JavaMailSender {

        @Override
        public MimeMessage createMimeMessage() {
            try {
                return new MimeMessage((jakarta.mail.Session) null);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public MimeMessage createMimeMessage(InputStream contentStream) throws MailException {
            return createMimeMessage();
        }

        @Override
        public void send(MimeMessage mimeMessage) throws MailException {
            log.info("[TEST STUB] Email (MimeMessage) — suppressed");
        }

        @Override
        public void send(MimeMessage... mimeMessages) throws MailException {
            log.info("[TEST STUB] Email ({} MimeMessages) — suppressed", mimeMessages.length);
        }

        @Override
        public void send(MimeMessagePreparator mimeMessagePreparator) throws MailException {
            log.info("[TEST STUB] Email (MimeMessagePreparator) — suppressed");
        }

        @Override
        public void send(MimeMessagePreparator... mimeMessagePreparators) throws MailException {
            log.info("[TEST STUB] Email ({} MimeMessagePreparators) — suppressed", mimeMessagePreparators.length);
        }

        @Override
        public void send(SimpleMailMessage simpleMessage) throws MailException {
            log.info("[TEST STUB] Email to {} subject='{}' — suppressed",
                    simpleMessage.getTo(), simpleMessage.getSubject());
        }

        @Override
        public void send(SimpleMailMessage... simpleMessages) throws MailException {
            log.info("[TEST STUB] Email ({} SimpleMailMessages) — suppressed", simpleMessages.length);
        }

        private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(NoOpJavaMailSender.class);
    }

    // ── Helper: build a stub PaymentIntent via reflection ──

    private static PaymentIntent buildStubPaymentIntent(String id, String clientSecret, String status) {
        PaymentIntent pi = new PaymentIntent();
        setField(pi, "id", id);
        setField(pi, "clientSecret", clientSecret);
        setField(pi, "status", status);
        return pi;
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            Field field = findField(target.getClass(), fieldName);
            if (field != null) {
                field.setAccessible(true);
                field.set(target, value);
            }
        } catch (Exception e) {
            log.warn("[TEST STUB] Could not set field '{}' on {}: {}", fieldName, target.getClass().getSimpleName(), e.getMessage());
        }
    }

    private static Field findField(Class<?> clazz, String fieldName) {
        while (clazz != null) {
            try {
                return clazz.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        return null;
    }
}


