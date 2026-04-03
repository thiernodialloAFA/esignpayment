package com.esign.payment.service;

import com.esign.payment.model.AccountApplication;
import com.esign.payment.model.Document;
import com.esign.payment.model.Signature;
import com.esign.payment.repository.SignatureRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ContractPdfService {

    private final SignatureRepository signatureRepository;

    private static final String CONTRACTS_DIR = "uploads/contracts";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    // ── Fonts ──
    private PDType1Font fontBold() { return new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD); }
    private PDType1Font fontRegular() { return new PDType1Font(Standard14Fonts.FontName.HELVETICA); }
    private PDType1Font fontItalic() { return new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE); }

    /**
     * Generates a contract PDF for the given application and writes it to disk.
     * Returns the file path where the PDF was saved.
     */
    public String generateContractPdf(AccountApplication app) {
        try {
            Files.createDirectories(Paths.get(CONTRACTS_DIR));
            String fileName = "contrat_" + app.getReferenceNumber() + ".pdf";
            Path filePath = Paths.get(CONTRACTS_DIR, fileName);

            try (PDDocument doc = new PDDocument()) {
                // ── Page 1: Contract ──
                PDPage page1 = new PDPage(PDRectangle.A4);
                doc.addPage(page1);

                float margin = 60;
                float pageWidth = page1.getMediaBox().getWidth();
                float contentWidth = pageWidth - 2 * margin;
                float y = page1.getMediaBox().getHeight() - margin;

                try (PDPageContentStream cs = new PDPageContentStream(doc, page1)) {
                    // Header
                    y = drawCenteredText(cs, "ESIGN BANK", fontBold(), 22, y, pageWidth);
                    y -= 6;
                    y = drawCenteredText(cs, "Établissement Bancaire Numérique", fontItalic(), 10, y, pageWidth);
                    y -= 4;
                    y = drawLine(cs, margin, y, pageWidth - margin, y);
                    y -= 24;

                    // Title
                    y = drawCenteredText(cs, "CONTRAT D'OUVERTURE DE COMPTE BANCAIRE", fontBold(), 16, y, pageWidth);
                    y -= 28;

                    // Reference & Date
                    y = drawText(cs, "Référence : " + app.getReferenceNumber(), fontBold(), 11, margin, y);
                    y -= 16;
                    y = drawText(cs, "Date : " + LocalDate.now().format(DATE_FMT), fontRegular(), 11, margin, y);
                    y -= 24;

                    // Section: Account Info
                    y = drawText(cs, "1. TYPE DE COMPTE", fontBold(), 13, margin, y);
                    y -= 18;
                    y = drawLabelValue(cs, "Type de compte :", app.getAccountType().getLabel(), margin, y);
                    y -= 16;
                    String fee = app.getAccountType().getMonthlyFee().doubleValue() > 0
                            ? app.getAccountType().getMonthlyFee() + " €/mois"
                            : "Gratuit";
                    y = drawLabelValue(cs, "Frais mensuels :", fee, margin, y);
                    y -= 16;
                    if (app.getAccountType().getDescription() != null) {
                        y = drawLabelValue(cs, "Description :", app.getAccountType().getDescription(), margin, y);
                    }
                    y -= 24;

                    // Section: Client Info
                    y = drawText(cs, "2. INFORMATIONS DU TITULAIRE", fontBold(), 13, margin, y);
                    y -= 18;
                    String fullName = app.getUser().getFirstName() + " " + app.getUser().getLastName();
                    y = drawLabelValue(cs, "Nom complet :", fullName, margin, y);
                    y -= 16;
                    y = drawLabelValue(cs, "Email :", app.getUser().getEmail(), margin, y);
                    y -= 16;
                    if (app.getDateOfBirth() != null) {
                        y = drawLabelValue(cs, "Date de naissance :", app.getDateOfBirth().format(DATE_FMT), margin, y);
                        y -= 16;
                    }
                    if (app.getPhoneNumber() != null) {
                        y = drawLabelValue(cs, "Téléphone :", app.getPhoneNumber(), margin, y);
                        y -= 16;
                    }
                    if (app.getNationality() != null) {
                        y = drawLabelValue(cs, "Nationalité :", app.getNationality(), margin, y);
                        y -= 16;
                    }
                    y -= 8;

                    // Section: Address
                    y = drawText(cs, "3. ADRESSE", fontBold(), 13, margin, y);
                    y -= 18;
                    if (app.getAddressLine1() != null) {
                        y = drawLabelValue(cs, "Adresse :", app.getAddressLine1(), margin, y);
                        y -= 16;
                    }
                    if (app.getAddressLine2() != null && !app.getAddressLine2().isBlank()) {
                        y = drawLabelValue(cs, "Complément :", app.getAddressLine2(), margin, y);
                        y -= 16;
                    }
                    String cityPostal = (app.getCity() != null ? app.getCity() : "") +
                            (app.getPostalCode() != null ? " " + app.getPostalCode() : "");
                    if (!cityPostal.isBlank()) {
                        y = drawLabelValue(cs, "Ville :", cityPostal, margin, y);
                        y -= 16;
                    }
                    if (app.getCountry() != null) {
                        y = drawLabelValue(cs, "Pays :", app.getCountry(), margin, y);
                        y -= 16;
                    }
                    y -= 8;

                    // Section: Employment
                    y = drawText(cs, "4. SITUATION PROFESSIONNELLE", fontBold(), 13, margin, y);
                    y -= 18;
                    if (app.getEmploymentStatus() != null) {
                        y = drawLabelValue(cs, "Statut :", app.getEmploymentStatus().name(), margin, y);
                        y -= 16;
                    }
                    if (app.getEmployerName() != null && !app.getEmployerName().isBlank()) {
                        y = drawLabelValue(cs, "Employeur :", app.getEmployerName(), margin, y);
                        y -= 16;
                    }
                    if (app.getMonthlyIncome() != null) {
                        y = drawLabelValue(cs, "Revenu mensuel :", app.getMonthlyIncome() + " €", margin, y);
                        y -= 16;
                    }
                    y -= 24;

                    // Terms
                    y = drawText(cs, "5. CONDITIONS GÉNÉRALES", fontBold(), 13, margin, y);
                    y -= 18;
                    String[] terms = {
                            "Le titulaire certifie l'exactitude des informations fournies.",
                            "Le compte sera activé après vérification des documents KYC.",
                            "Les frais bancaires seront prélevés mensuellement selon le type de compte choisi.",
                            "Le titulaire peut clôturer le compte à tout moment en respectant un préavis de 30 jours.",
                            "La banque se réserve le droit de refuser l'ouverture de compte si les vérifications requises échouent."
                    };
                    for (String term : terms) {
                        y = drawWrappedText(cs, "• " + term, fontRegular(), 10, margin + 10, y, contentWidth - 10);
                        y -= 12;
                    }
                    y -= 16;

                    // Signature zone
                    y = drawLine(cs, margin, y, pageWidth - margin, y);
                    y -= 20;
                    y = drawText(cs, "SIGNATURE DU TITULAIRE", fontBold(), 12, margin, y);
                    y -= 16;
                    y = drawText(cs, "Lu et approuvé, le " + LocalDate.now().format(DATE_FMT), fontItalic(), 10, margin, y);
                    y -= 20;

                    // Signature placeholder box
                    cs.setLineWidth(0.5f);
                    cs.addRect(margin, y - 80, 250, 80);
                    cs.stroke();
                    drawText(cs, "(Signature)", fontItalic(), 9, margin + 90, y - 90);

                    // Footer
                    float footerY = 40;
                    drawCenteredText(cs, "ESign Bank — Document généré le " + LocalDate.now().format(DATE_FMT)
                            + " — Réf. " + app.getReferenceNumber(), fontItalic(), 8, footerY, pageWidth);
                }

                doc.save(filePath.toFile());
            }

            log.info("Contract PDF generated at: {}", filePath);
            return filePath.toString();

        } catch (IOException e) {
            throw new RuntimeException("Failed to generate contract PDF: " + e.getMessage(), e);
        }
    }

    /**
     * Regenerates the contract PDF with signatures embedded,
     * overwriting the original file so there's only one document.
     */
    public void regenerateWithSignatures(Document document) {
        try {
            List<Signature> signatures = signatureRepository.findByDocumentId(document.getId());

            // Load the original PDF
            Path originalPath = Paths.get(document.getFilePath());
            if (!Files.exists(originalPath)) {
                throw new IOException("Original contract file not found: " + originalPath);
            }

            try (PDDocument pdfDoc = org.apache.pdfbox.Loader.loadPDF(originalPath.toFile())) {

                // Add a new page for signatures
                PDPage sigPage = new PDPage(PDRectangle.A4);
                pdfDoc.addPage(sigPage);

                float margin = 60;
                float pageWidth = sigPage.getMediaBox().getWidth();
                float y = sigPage.getMediaBox().getHeight() - margin;

                try (PDPageContentStream cs = new PDPageContentStream(pdfDoc, sigPage)) {
                    // Header
                    y = drawCenteredText(cs, "ESIGN BANK", fontBold(), 22, y, pageWidth);
                    y -= 6;
                    y = drawCenteredText(cs, "Page de Signatures", fontItalic(), 10, y, pageWidth);
                    y -= 4;
                    y = drawLine(cs, margin, y, pageWidth - margin, y);
                    y -= 24;

                    y = drawCenteredText(cs, "ATTESTATION DE SIGNATURES", fontBold(), 16, y, pageWidth);
                    y -= 8;
                    y = drawCenteredText(cs, document.getTitle(), fontRegular(), 12, y, pageWidth);
                    y -= 28;

                    y = drawText(cs, "Ce document a été signé électroniquement par les parties suivantes :", fontRegular(), 11, margin, y);
                    y -= 28;

                    // Each signature
                    int sigNum = 0;
                    for (Signature sig : signatures) {
                        sigNum++;
                        y = drawText(cs, "Signataire " + sigNum + " : " + sig.getSigner().getName(),
                                fontBold(), 12, margin, y);
                        y -= 16;
                        y = drawLabelValue(cs, "Email :", sig.getSigner().getEmail(), margin + 10, y);
                        y -= 14;
                        y = drawLabelValue(cs, "Date :", sig.getSignedAt().format(
                                DateTimeFormatter.ofPattern("dd/MM/yyyy à HH:mm:ss")), margin + 10, y);
                        y -= 14;
                        if (sig.getIpAddress() != null) {
                            y = drawLabelValue(cs, "Adresse IP :", sig.getIpAddress(), margin + 10, y);
                            y -= 14;
                        }

                        // Draw the signature image
                        try {
                            String sigData = sig.getSignatureData();
                            if (sigData != null && sigData.startsWith("data:image")) {
                                String base64 = sigData.substring(sigData.indexOf(",") + 1);
                                byte[] imgBytes = Base64.getDecoder().decode(base64);

                                BufferedImage buffered = ImageIO.read(new ByteArrayInputStream(imgBytes));
                                if (buffered != null) {
                                    ByteArrayOutputStream pngOut = new ByteArrayOutputStream();
                                    ImageIO.write(buffered, "png", pngOut);
                                    PDImageXObject pdImage = PDImageXObject.createFromByteArray(
                                            pdfDoc, pngOut.toByteArray(), "signature_" + sigNum + ".png");

                                    float sigWidth = 200;
                                    float sigHeight = 80;
                                    cs.drawImage(pdImage, margin + 10, y - sigHeight, sigWidth, sigHeight);
                                    y -= sigHeight + 10;
                                }
                            }
                        } catch (Exception e) {
                            log.warn("Could not embed signature image for signer {}: {}", sig.getSigner().getName(), e.getMessage());
                            y = drawText(cs, "[Signature électronique enregistrée]", fontItalic(), 10, margin + 10, y);
                            y -= 14;
                        }

                        y -= 10;
                        y = drawLine(cs, margin, y, pageWidth - margin, y);
                        y -= 20;
                    }

                    // Footer attestation
                    y -= 10;
                    y = drawText(cs, "Document signé électroniquement — Toutes les signatures ont été vérifiées.",
                            fontBold(), 10, margin, y);
                    y -= 14;
                    y = drawText(cs, "Horodatage : " + java.time.LocalDateTime.now().format(
                                    DateTimeFormatter.ofPattern("dd/MM/yyyy à HH:mm:ss")),
                            fontRegular(), 10, margin, y);

                    // Footer
                    float footerY = 40;
                    drawCenteredText(cs, "ESign Bank — Document signé électroniquement — " +
                            java.time.LocalDate.now().format(DATE_FMT), fontItalic(), 8, footerY, pageWidth);
                }

                // Overwrite the original file with the signed version
                pdfDoc.save(originalPath.toFile());
            }

            log.info("Contract PDF overwritten with signatures at: {}", originalPath);

        } catch (IOException e) {
            throw new RuntimeException("Failed to regenerate PDF with signatures: " + e.getMessage(), e);
        }
    }

    // ── Drawing helpers ──

    private float drawText(PDPageContentStream cs, String text, PDType1Font font, float size, float x, float y) throws IOException {
        cs.beginText();
        cs.setFont(font, size);
        cs.newLineAtOffset(x, y);
        cs.showText(text);
        cs.endText();
        return y;
    }

    private float drawCenteredText(PDPageContentStream cs, String text, PDType1Font font, float size, float y, float pageWidth) throws IOException {
        float textWidth = font.getStringWidth(text) / 1000 * size;
        float x = (pageWidth - textWidth) / 2;
        return drawText(cs, text, font, size, x, y);
    }

    private float drawLabelValue(PDPageContentStream cs, String label, String value, float x, float y) throws IOException {
        cs.beginText();
        cs.setFont(fontBold(), 10);
        cs.newLineAtOffset(x, y);
        cs.showText(label + " ");
        cs.setFont(fontRegular(), 10);
        cs.showText(value != null ? value : "—");
        cs.endText();
        return y;
    }

    private float drawLine(PDPageContentStream cs, float x1, float y1, float x2, float y2) throws IOException {
        cs.setLineWidth(1f);
        cs.moveTo(x1, y1);
        cs.lineTo(x2, y2);
        cs.stroke();
        return y1;
    }

    private float drawWrappedText(PDPageContentStream cs, String text, PDType1Font font, float size, float x, float y, float maxWidth) throws IOException {
        float spaceWidth = font.getStringWidth(" ") / 1000 * size;
        String[] words = text.split(" ");
        StringBuilder line = new StringBuilder();
        float lineWidth = 0;

        for (String word : words) {
            float wordWidth = font.getStringWidth(word) / 1000 * size;
            if (lineWidth + wordWidth > maxWidth && line.length() > 0) {
                drawText(cs, line.toString().trim(), font, size, x, y);
                y -= size + 2;
                line = new StringBuilder();
                lineWidth = 0;
            }
            line.append(word).append(" ");
            lineWidth += wordWidth + spaceWidth;
        }
        if (line.length() > 0) {
            drawText(cs, line.toString().trim(), font, size, x, y);
        }
        return y;
    }
}

