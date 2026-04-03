package com.esign.payment.service;

import com.esign.payment.model.DocumentSigner;
import com.esign.payment.repository.DocumentSignerRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class OtpService {

    private final DocumentSignerRepository documentSignerRepository;
    private final SmsService smsService;

    private static final int OTP_LENGTH = 6;
    private static final int OTP_EXPIRY_MINUTES = 5;
    private static final int MAX_OTP_ATTEMPTS = 5;

    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Generates and sends an OTP code to the signer's phone number.
     */
    @Transactional
    public void generateAndSendOtp(String token, String phoneNumber) {
        DocumentSigner signer = documentSignerRepository.findBySignatureToken(token)
                .orElseThrow(() -> new EntityNotFoundException("Invalid or expired signature token"));

        if (phoneNumber == null || phoneNumber.isBlank()) {
            throw new IllegalArgumentException("Phone number is required for OTP verification");
        }

        String otpCode = generateOtpCode();

        signer.setPhone(phoneNumber);
        signer.setOtpCode(otpCode);
        signer.setOtpExpiresAt(LocalDateTime.now().plusMinutes(OTP_EXPIRY_MINUTES));
        signer.setOtpVerified(false);
        signer.setOtpAttempts(0);
        documentSignerRepository.save(signer);

        smsService.sendOtpCode(phoneNumber, otpCode, signer.getName());
        log.info("OTP sent to signer {} for document signing", signer.getEmail());
    }

    /**
     * Verifies the OTP code provided by the signer.
     */
    @Transactional
    public boolean verifyOtp(String token, String otpCode) {
        DocumentSigner signer = documentSignerRepository.findBySignatureToken(token)
                .orElseThrow(() -> new EntityNotFoundException("Invalid or expired signature token"));

        if (signer.getOtpCode() == null) {
            throw new IllegalStateException("No OTP has been sent. Please request an OTP first.");
        }

        if (signer.getOtpAttempts() >= MAX_OTP_ATTEMPTS) {
            throw new IllegalStateException("Maximum OTP attempts exceeded. Please request a new code.");
        }

        if (signer.getOtpExpiresAt() != null && signer.getOtpExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalStateException("OTP has expired. Please request a new code.");
        }

        signer.setOtpAttempts(signer.getOtpAttempts() + 1);

        if (constantTimeEquals(signer.getOtpCode(), otpCode)) {
            signer.setOtpVerified(true);
            documentSignerRepository.save(signer);
            log.info("OTP verified for signer {}", signer.getEmail());
            return true;
        }

        documentSignerRepository.save(signer);
        log.warn("Invalid OTP attempt for signer {} (attempt {})", signer.getEmail(), signer.getOtpAttempts());
        return false;
    }

    /**
     * Checks if the signer has a verified OTP.
     */
    public boolean isOtpVerified(String token) {
        DocumentSigner signer = documentSignerRepository.findBySignatureToken(token)
                .orElseThrow(() -> new EntityNotFoundException("Invalid or expired signature token"));
        return signer.isOtpVerified();
    }

    private String generateOtpCode() {
        int code = secureRandom.nextInt(900000) + 100000;
        return String.valueOf(code);
    }

    /**
     * Constant-time string comparison to prevent timing attacks.
     */
    private boolean constantTimeEquals(String a, String b) {
        byte[] aBytes = a.getBytes(StandardCharsets.UTF_8);
        byte[] bBytes = b.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(aBytes, bBytes);
    }
}
