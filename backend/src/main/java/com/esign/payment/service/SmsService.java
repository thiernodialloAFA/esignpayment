package com.esign.payment.service;

import com.esign.payment.config.TwilioConfig;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class SmsService {

    private final TwilioConfig twilioConfig;

    /**
     * Normalizes a phone number to E.164 format required by Twilio.
     * Handles common formats: 0033..., 06..., 07..., +33...
     */
    private String normalizePhoneNumber(String phoneNumber) {
        // Remove all spaces, dashes, dots, parentheses
        String cleaned = phoneNumber.replaceAll("[\\s\\-\\.\\(\\)]", "");

        // 0033... → +33...
        if (cleaned.startsWith("00")) {
            cleaned = "+" + cleaned.substring(2);
        }
        // 06... or 07... (French mobile) → +336... or +337...
        else if (cleaned.startsWith("0") && cleaned.length() == 10) {
            cleaned = "+33" + cleaned.substring(1);
        }
        // Already has + prefix → keep as-is
        else if (!cleaned.startsWith("+")) {
            cleaned = "+" + cleaned;
        }

        return cleaned;
    }

    /**
     * Sends an SMS message to the specified phone number.
     */
    public void sendSms(String toPhoneNumber, String messageBody) {
        String normalizedNumber = normalizePhoneNumber(toPhoneNumber);
        log.info("Sending SMS to {} (normalized: {})", toPhoneNumber, normalizedNumber);

        Message message = Message.creator(
                new PhoneNumber(normalizedNumber),
                new PhoneNumber(twilioConfig.getPhoneNumber()),
                messageBody
        ).create();

        log.info("SMS sent to {} with SID: {}", normalizedNumber, message.getSid());
    }

    /**
     * Sends an OTP code via SMS for signature verification.
     */
    public void sendOtpCode(String toPhoneNumber, String otpCode, String signerName) {
        String message = String.format("Code: %s (expire 5min)", otpCode);
        sendSms(toPhoneNumber, message);
    }

    /**
     * Sends a signing invitation with a link via SMS.
     */
    public void sendSigningLink(String toPhoneNumber, String signerName, String documentTitle, String signingUrl) {
        String message = String.format("Signez ici: %s", signingUrl);
        sendSms(toPhoneNumber, message);
    }
}
