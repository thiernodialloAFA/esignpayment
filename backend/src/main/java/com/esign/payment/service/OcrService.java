package com.esign.payment.service;

import com.esign.payment.dto.response.OcrVerificationDetail;
import com.esign.payment.model.AccountApplication;
import com.esign.payment.model.enums.KycDocumentType;
import com.esign.payment.model.enums.OcrVerificationStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class OcrService {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.ocr.enabled:true}")
    private boolean ocrEnabled;

    @Value("${app.ocr.languages:fra+eng}")
    private String ocrLanguages;

    // ── Public verification methods ──

    /**
     * Verify a KYC document using OCR and return verification result as an OcrResult record.
     */
    public OcrResult verifyDocument(byte[] fileContent, String contentType,
                                    KycDocumentType documentType, AccountApplication app) {
        if (!ocrEnabled) {
            return new OcrResult(OcrVerificationStatus.NOT_AVAILABLE, null, 0,
                    List.of(), false, List.of("OCR désactivé"));
        }

        if (!isTesseractAvailable()) {
            log.warn("Tesseract OCR is not installed – skipping OCR verification");
            return new OcrResult(OcrVerificationStatus.NOT_AVAILABLE, null, 0,
                    List.of(), false, List.of("Tesseract non disponible sur ce serveur"));
        }

        try {
            String extractedText = extractText(fileContent, contentType);

            if (extractedText == null || extractedText.isBlank()) {
                return new OcrResult(OcrVerificationStatus.FAILED, "", 0,
                        List.of(), false, List.of("Impossible d'extraire le texte du document"));
            }

            return switch (documentType) {
                case ID_CARD, PASSPORT -> verifyIdDocument(extractedText, app);
                case PROOF_OF_ADDRESS -> verifyProofOfAddress(extractedText, app);
                default -> new OcrResult(OcrVerificationStatus.NOT_AVAILABLE, extractedText, 0,
                        List.of(), true, List.of());
            };
        } catch (Exception e) {
            log.error("OCR verification failed", e);
            return new OcrResult(OcrVerificationStatus.FAILED, "", 0,
                    List.of(), false, List.of("Erreur OCR: " + e.getMessage()));
        }
    }

    public String serializeDetails(List<OcrVerificationDetail> details) {
        try {
            return objectMapper.writeValueAsString(details);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    public String serializeWarnings(List<String> warnings) {
        try {
            return objectMapper.writeValueAsString(warnings);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    public List<OcrVerificationDetail> deserializeDetails(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, OcrVerificationDetail.class));
        } catch (Exception e) {
            return List.of();
        }
    }

    public List<String> deserializeWarnings(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (Exception e) {
            return List.of();
        }
    }

    // ── ID Document Verification ──

    private OcrResult verifyIdDocument(String text, AccountApplication app) {
        List<OcrVerificationDetail> details = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // 1. Check if it looks like an ID document
        boolean isIdDoc = isIdentityDocument(text);
        if (!isIdDoc) {
            warnings.add("Le document ne semble pas être une pièce d'identité officielle");
        }

        // 2. Try MRZ parsing first (most reliable)
        MrzData mrz = parseMrz(text);
        String firstName = app.getUser().getFirstName();
        String lastName = app.getUser().getLastName();
        LocalDate dob = app.getDateOfBirth();

        if (mrz != null) {
            log.info("MRZ detected – using MRZ data for verification");

            // Verify last name from MRZ
            if (lastName != null && !lastName.isBlank()) {
                int score = fuzzyMatchScore(normalize(lastName), normalize(mrz.lastName));
                details.add(OcrVerificationDetail.builder()
                        .fieldName("lastName").fieldLabel("Nom")
                        .declaredValue(lastName).extractedValue(mrz.lastName)
                        .matchScore(score).matched(score >= 70).build());
            }

            // Verify first name from MRZ
            if (firstName != null && !firstName.isBlank()) {
                int score = fuzzyMatchScore(normalize(firstName), normalize(mrz.firstName));
                details.add(OcrVerificationDetail.builder()
                        .fieldName("firstName").fieldLabel("Prénom")
                        .declaredValue(firstName).extractedValue(mrz.firstName)
                        .matchScore(score).matched(score >= 70).build());
            }

            // Verify DOB from MRZ
            if (dob != null && mrz.dateOfBirth != null) {
                boolean match = dob.equals(mrz.dateOfBirth);
                String declaredStr = dob.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
                String extractedStr = mrz.dateOfBirth.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
                details.add(OcrVerificationDetail.builder()
                        .fieldName("dateOfBirth").fieldLabel("Date de naissance")
                        .declaredValue(declaredStr).extractedValue(extractedStr)
                        .matchScore(match ? 100 : 0).matched(match).build());
            }
        } else {
            // Fallback: text-based matching
            log.info("No MRZ detected – falling back to text-based matching");

            if (lastName != null && !lastName.isBlank()) {
                int score = searchFieldInText(text, lastName);
                details.add(OcrVerificationDetail.builder()
                        .fieldName("lastName").fieldLabel("Nom")
                        .declaredValue(lastName).extractedValue(score >= 50 ? "Trouvé dans le texte" : "Non trouvé")
                        .matchScore(score).matched(score >= 50).build());
            }

            if (firstName != null && !firstName.isBlank()) {
                int score = searchFieldInText(text, firstName);
                details.add(OcrVerificationDetail.builder()
                        .fieldName("firstName").fieldLabel("Prénom")
                        .declaredValue(firstName).extractedValue(score >= 50 ? "Trouvé dans le texte" : "Non trouvé")
                        .matchScore(score).matched(score >= 50).build());
            }

            if (dob != null) {
                boolean found = searchDateInText(text, dob);
                String declaredStr = dob.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
                details.add(OcrVerificationDetail.builder()
                        .fieldName("dateOfBirth").fieldLabel("Date de naissance")
                        .declaredValue(declaredStr).extractedValue(found ? "Trouvé" : "Non trouvé")
                        .matchScore(found ? 100 : 0).matched(found).build());
            }
        }

        int overallScore = calculateOverallScore(details);
        OcrVerificationStatus status = overallScore >= 60 ? OcrVerificationStatus.VERIFIED : OcrVerificationStatus.MISMATCH;

        return new OcrResult(status, text, overallScore, details, isIdDoc, warnings);
    }

    // ── Proof of Address Verification ──

    private OcrResult verifyProofOfAddress(String text, AccountApplication app) {
        List<OcrVerificationDetail> details = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        boolean isAddressDoc = isAddressDocument(text);
        if (!isAddressDoc) {
            warnings.add("Le document ne semble pas être un justificatif de domicile");
        }

        // Check full name
        String fullName = (app.getUser().getFirstName() + " " + app.getUser().getLastName()).trim();
        if (!fullName.isBlank()) {
            int score = searchFieldInText(text, fullName);
            // Also try last name only
            int lastNameScore = searchFieldInText(text, app.getUser().getLastName());
            int bestScore = Math.max(score, lastNameScore);
            details.add(OcrVerificationDetail.builder()
                    .fieldName("name").fieldLabel("Nom")
                    .declaredValue(fullName)
                    .extractedValue(bestScore >= 50 ? "Trouvé dans le document" : "Non trouvé")
                    .matchScore(bestScore).matched(bestScore >= 50).build());
        }

        // Check address
        if (app.getAddressLine1() != null && !app.getAddressLine1().isBlank()) {
            int score = searchFieldInText(text, app.getAddressLine1());
            details.add(OcrVerificationDetail.builder()
                    .fieldName("address").fieldLabel("Adresse")
                    .declaredValue(app.getAddressLine1())
                    .extractedValue(score >= 40 ? "Trouvé dans le document" : "Non trouvé")
                    .matchScore(score).matched(score >= 40).build());
        }

        // Check city
        if (app.getCity() != null && !app.getCity().isBlank()) {
            int score = searchFieldInText(text, app.getCity());
            details.add(OcrVerificationDetail.builder()
                    .fieldName("city").fieldLabel("Ville")
                    .declaredValue(app.getCity())
                    .extractedValue(score >= 60 ? "Trouvé" : "Non trouvé")
                    .matchScore(score).matched(score >= 60).build());
        }

        // Check postal code (exact match expected)
        if (app.getPostalCode() != null && !app.getPostalCode().isBlank()) {
            boolean found = text.contains(app.getPostalCode());
            details.add(OcrVerificationDetail.builder()
                    .fieldName("postalCode").fieldLabel("Code postal")
                    .declaredValue(app.getPostalCode())
                    .extractedValue(found ? app.getPostalCode() : "Non trouvé")
                    .matchScore(found ? 100 : 0).matched(found).build());
        }

        int overallScore = calculateOverallScore(details);
        OcrVerificationStatus status = overallScore >= 50 ? OcrVerificationStatus.VERIFIED : OcrVerificationStatus.MISMATCH;

        return new OcrResult(status, text, overallScore, details, isAddressDoc, warnings);
    }

    // ── Text Extraction ──

    private String extractText(byte[] fileContent, String contentType) throws Exception {
        if (contentType != null && contentType.contains("pdf")) {
            return extractTextFromPdf(fileContent);
        } else {
            return extractTextFromImage(fileContent);
        }
    }

    private String extractTextFromImage(byte[] imageBytes) throws Exception {
        Path tempInput = Files.createTempFile("ocr_input_", ".png");
        try {
            Files.write(tempInput, imageBytes);
            return runTesseract(tempInput);
        } finally {
            Files.deleteIfExists(tempInput);
        }
    }

    private String extractTextFromPdf(byte[] pdfBytes) throws Exception {
        StringBuilder allText = new StringBuilder();
        try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
            PDFRenderer renderer = new PDFRenderer(doc);
            int pages = Math.min(doc.getNumberOfPages(), 3); // Limit to first 3 pages
            for (int i = 0; i < pages; i++) {
                BufferedImage image = renderer.renderImageWithDPI(i, 300);
                Path tempImage = Files.createTempFile("ocr_pdf_page_", ".png");
                try {
                    ImageIO.write(image, "png", tempImage.toFile());
                    allText.append(runTesseract(tempImage)).append("\n");
                } finally {
                    Files.deleteIfExists(tempImage);
                }
            }
        }
        return allText.toString();
    }

    private String runTesseract(Path imagePath) throws Exception {
        Path outputBase = Files.createTempFile("ocr_out_", "");
        Path outputFile = Path.of(outputBase + ".txt");
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "tesseract", imagePath.toString(), outputBase.toString(),
                    "-l", ocrLanguages, "--psm", "3"
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();

            // Read stdout/stderr
            String processOutput;
            try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                processOutput = reader.lines().collect(Collectors.joining("\n"));
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                log.warn("Tesseract exited with code {}: {}", exitCode, processOutput);
            }

            if (Files.exists(outputFile)) {
                return Files.readString(outputFile).trim();
            }
            return "";
        } finally {
            Files.deleteIfExists(outputFile);
            Files.deleteIfExists(outputBase);
        }
    }

    private boolean isTesseractAvailable() {
        try {
            ProcessBuilder pb = new ProcessBuilder("tesseract", "--version");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            int code = process.waitFor();
            return code == 0;
        } catch (Exception e) {
            return false;
        }
    }

    // ── Document Type Detection ──

    private boolean isIdentityDocument(String text) {
        String upper = text.toUpperCase();
        String[] idKeywords = {
                "CARTE NATIONALE", "CARTE D'IDENTITE", "CARTE D'IDENTITÉ",
                "REPUBLIQUE FRANCAISE", "RÉPUBLIQUE FRANÇAISE",
                "IDENTITY CARD", "PASSEPORT", "PASSPORT",
                "NATIONALITE", "NATIONALITÉ", "NOM", "PRENOM", "PRÉNOM",
                "DATE DE NAISSANCE", "SEXE", "IDFRA", "P<FRA"
        };
        int found = 0;
        for (String kw : idKeywords) {
            if (upper.contains(kw)) found++;
        }
        return found >= 2;
    }

    private boolean isAddressDocument(String text) {
        String upper = text.toUpperCase();
        String[] addressKeywords = {
                "FACTURE", "QUITTANCE", "AVIS D'IMPOSITION", "AVIS D'IMPOT",
                "ATTESTATION", "RELEVÉ", "RELEVE", "JUSTIFICATIF",
                "EDF", "ENGIE", "GDF", "FREE", "SFR", "ORANGE", "BOUYGUES",
                "TAXE D'HABITATION", "ASSURANCE HABITATION",
                "LOYER", "BAIL", "LOCATAIRE", "DOMICILE",
                "VEOLIA", "SUEZ", "TOTAL ENERGIES", "TOTALENERGIES",
                "ÉLECTRICITÉ", "ELECTRICITE", "GAZ", "EAU",
                "ADRESSE", "CODE POSTAL"
        };
        int found = 0;
        for (String kw : addressKeywords) {
            if (upper.contains(kw)) found++;
        }
        return found >= 2;
    }

    // ── MRZ Parsing ──

    /**
     * Parse Machine Readable Zone from OCR text.
     * Supports TD1 (ID cards, 3 lines × 30 chars) and TD3 (passports, 2 lines × 44 chars).
     */
    private MrzData parseMrz(String text) {
        // Clean up text and look for MRZ-like lines
        List<String> mrzLines = new ArrayList<>();
        for (String line : text.split("\n")) {
            String cleaned = line.trim().replaceAll("[^A-Z0-9<]", "");
            if (cleaned.length() >= 28 && cleaned.matches("[A-Z0-9<]+") && cleaned.contains("<")) {
                mrzLines.add(cleaned);
            }
        }

        if (mrzLines.size() < 2) return null;

        try {
            // Try TD3 (passport): 2 lines of 44 chars
            if (mrzLines.size() >= 2 && mrzLines.get(mrzLines.size() - 2).length() >= 42) {
                return parseTd3(mrzLines.get(mrzLines.size() - 2), mrzLines.get(mrzLines.size() - 1));
            }

            // Try TD1 (ID card): 3 lines of 30 chars
            if (mrzLines.size() >= 3) {
                return parseTd1(mrzLines.get(mrzLines.size() - 3),
                        mrzLines.get(mrzLines.size() - 2),
                        mrzLines.get(mrzLines.size() - 1));
            }

            // Try TD1 with just 2 lines (sometimes line 1 is missed)
            if (mrzLines.size() >= 2) {
                String l1 = mrzLines.get(0);
                String l2 = mrzLines.get(1);
                // Check if first line starts with ID type indicator
                if (l1.startsWith("ID") || l1.startsWith("I<") || l1.startsWith("P<")) {
                    return parseTd3(l1, l2);
                }
            }
        } catch (Exception e) {
            log.debug("MRZ parsing failed: {}", e.getMessage());
        }

        return null;
    }

    private MrzData parseTd3(String line1, String line2) {
        // Line 1: P<FRALASTNAME<<FIRSTNAME<MIDDLE<<<
        // Line 2: DOC_NUM<<CHECK<FRA YYMMDD CHECK SEX YYMMDD CHECK ...
        if (line1.length() < 30 || line2.length() < 30) return null;

        String namePart = line1.substring(5); // skip "P<FRA" or similar
        String[] names = namePart.split("<<", 2);
        String lastName = names[0].replace("<", " ").trim();
        String firstName = names.length > 1 ? names[1].replace("<", " ").trim() : "";

        // Parse DOB from line 2 (positions 13-18 for TD3: YYMMDD)
        LocalDate dob = null;
        if (line2.length() >= 20) {
            String dobStr = line2.substring(13, 19);
            dob = parseMrzDate(dobStr);
        }

        if (lastName.isBlank()) return null;

        return new MrzData(lastName, firstName, dob);
    }

    private MrzData parseTd1(String line1, String line2, String line3) {
        // TD1 Line 1: IDFRA[doc_number]..
        // TD1 Line 2: YYMMDD[check]SEX...
        // TD1 Line 3: LASTNAME<<FIRSTNAME<<<<
        if (line3.length() < 20) return null;

        String[] names = line3.split("<<", 2);
        String lastName = names[0].replace("<", " ").trim();
        String firstName = names.length > 1 ? names[1].replace("<", " ").trim() : "";

        // DOB is in line2 positions 0-5
        LocalDate dob = null;
        if (line2.length() >= 7) {
            String dobStr = line2.substring(0, 6);
            dob = parseMrzDate(dobStr);
        }

        if (lastName.isBlank()) return null;

        return new MrzData(lastName, firstName, dob);
    }

    private LocalDate parseMrzDate(String yymmdd) {
        try {
            if (yymmdd.length() != 6) return null;
            int yy = Integer.parseInt(yymmdd.substring(0, 2));
            int mm = Integer.parseInt(yymmdd.substring(2, 4));
            int dd = Integer.parseInt(yymmdd.substring(4, 6));
            int year = yy > 50 ? 1900 + yy : 2000 + yy;
            return LocalDate.of(year, mm, dd);
        } catch (Exception e) {
            return null;
        }
    }

    // ── String Matching Utilities ──

    private int searchFieldInText(String text, String fieldValue) {
        String normalizedText = normalize(text);
        String normalizedField = normalize(fieldValue);

        // Exact contain
        if (normalizedText.contains(normalizedField)) return 100;

        // Word-by-word matching
        String[] words = normalizedField.split("\\s+");
        int matchedWords = 0;
        for (String word : words) {
            if (word.length() >= 3 && normalizedText.contains(word)) {
                matchedWords++;
            }
        }
        if (words.length > 0) {
            int wordScore = (matchedWords * 100) / words.length;
            if (wordScore > 0) return wordScore;
        }

        // Fuzzy: check each word in text against field words
        String[] textWords = normalizedText.split("\\s+");
        int bestWordScore = 0;
        for (String fieldWord : words) {
            if (fieldWord.length() < 3) continue;
            for (String textWord : textWords) {
                if (textWord.length() < 3) continue;
                int score = fuzzyMatchScore(fieldWord, textWord);
                bestWordScore = Math.max(bestWordScore, score);
            }
        }

        return bestWordScore >= 80 ? bestWordScore : 0;
    }

    private boolean searchDateInText(String text, LocalDate date) {
        String[] formats = {
                date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                date.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")),
                date.format(DateTimeFormatter.ofPattern("dd-MM-yyyy")),
                date.format(DateTimeFormatter.ofPattern("ddMMyyyy")),
                date.format(DateTimeFormatter.ofPattern("dd/MM/yy")),
                date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")),
        };
        String normalizedText = text.replaceAll("\\s+", " ");
        for (String fmt : formats) {
            if (normalizedText.contains(fmt)) return true;
        }
        // Also check MRZ-style: YYMMDD
        String mrzDate = String.format("%02d%02d%02d",
                date.getYear() % 100, date.getMonthValue(), date.getDayOfMonth());
        return normalizedText.contains(mrzDate);
    }

    private int fuzzyMatchScore(String s1, String s2) {
        if (s1.equals(s2)) return 100;
        if (s1.isEmpty() || s2.isEmpty()) return 0;
        int distance = levenshteinDistance(s1, s2);
        int maxLen = Math.max(s1.length(), s2.length());
        return Math.max(0, 100 - (distance * 100 / maxLen));
    }

    private int levenshteinDistance(String a, String b) {
        int[][] dp = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) dp[i][0] = i;
        for (int j = 0; j <= b.length(); j++) dp[0][j] = j;
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1), dp[i - 1][j - 1] + cost);
            }
        }
        return dp[a.length()][b.length()];
    }

    private String normalize(String s) {
        if (s == null) return "";
        return java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
                .replaceAll("[\\p{InCombiningDiacriticalMarks}]", "")
                .toLowerCase()
                .replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private int calculateOverallScore(List<OcrVerificationDetail> details) {
        if (details.isEmpty()) return 0;
        return (int) details.stream().mapToInt(OcrVerificationDetail::getMatchScore).average().orElse(0);
    }

    // ── Inner types ──

    public record OcrResult(
            OcrVerificationStatus status,
            String extractedText,
            int matchScore,
            List<OcrVerificationDetail> details,
            boolean documentTypeValid,
            List<String> warnings
    ) {}

    private record MrzData(String lastName, String firstName, LocalDate dateOfBirth) {}
}


