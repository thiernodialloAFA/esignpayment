package com.esign.payment.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:noreply@esignpay.com}")
    private String fromAddress;

    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    public void sendContractSigningEmail(String toEmail, String recipientName, String signatureToken, String referenceNumber) {
        String signingUrl = frontendUrl + "/sign/" + signatureToken;
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(toEmail);
        message.setSubject("Contrat à signer - Demande " + referenceNumber);
        message.setText(String.format(
                "Bonjour %s,\n\n" +
                "Votre demande d'ouverture de compte (%s) a été approuvée.\n" +
                "Veuillez signer votre contrat en cliquant sur le lien suivant :\n\n%s\n\n" +
                "Cordialement,\nL'équipe ESignPay",
                recipientName, referenceNumber, signingUrl
        ));

        try {
            mailSender.send(message);
            log.info("Contract signing email sent to {} for application {}", toEmail, referenceNumber);
        } catch (Exception e) {
            log.error("Failed to send contract email to {}: {}", toEmail, e.getMessage());
        }
    }

    public void sendStatusUpdateEmail(String toEmail, String recipientName, String referenceNumber, String newStatus) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(toEmail);
        message.setSubject("Mise à jour - Demande " + referenceNumber);
        message.setText(String.format(
                "Bonjour %s,\n\n" +
                "Le statut de votre demande d'ouverture de compte (%s) a été mis à jour : %s.\n\n" +
                "Connectez-vous pour suivre votre demande.\n\n" +
                "Cordialement,\nL'équipe ESignPay",
                recipientName, referenceNumber, newStatus
        ));

        try {
            mailSender.send(message);
            log.info("Status update email sent to {} for application {}", toEmail, referenceNumber);
        } catch (Exception e) {
            log.error("Failed to send status email to {}: {}", toEmail, e.getMessage());
        }
    }
}

