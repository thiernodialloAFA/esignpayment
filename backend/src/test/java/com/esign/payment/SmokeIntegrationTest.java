package com.esign.payment;

import com.esign.payment.config.TestDataInitializer;
import com.esign.payment.config.TestSecurityConfig;
import com.esign.payment.model.AccountApplication;
import com.esign.payment.model.Document;
import com.esign.payment.model.Payment;
import com.esign.payment.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Smoke integration tests — validates all main GET endpoints return 200 with seed data,
 * and that security is completely disabled (no 401/403).
 * <p>
 * Uses spring-security-test's jwt() post-processor to inject a test JWT matching
 * the seed user from TestDataInitializer.
 * </p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestSecurityConfig.class, TestDataInitializer.class})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SmokeIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private AccountApplicationRepository accountApplicationRepository;

    /**
     * Builds the test JWT matching seed data.
     */
    private Jwt testJwt() {
        return TestSecurityConfig.buildTestJwt();
    }

    // ═══════════════════════════════════════════════════════════════════
    // AUTH
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @Order(1)
    @DisplayName("GET /api/auth/me — returns current test user (no auth required)")
    void getCurrentUser() throws Exception {
        mockMvc.perform(get("/api/auth/me").with(jwt().jwt(testJwt())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value(TestSecurityConfig.TEST_EMAIL))
                .andExpect(jsonPath("$.data.firstName").value(TestSecurityConfig.TEST_FIRST_NAME))
                .andExpect(jsonPath("$.data.lastName").value(TestSecurityConfig.TEST_LAST_NAME));
    }

    // ═══════════════════════════════════════════════════════════════════
    // DOCUMENTS
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @Order(10)
    @DisplayName("GET /api/documents — lists seed documents")
    void listDocuments() throws Exception {
        mockMvc.perform(get("/api/documents").with(jwt().jwt(testJwt())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content", hasSize(greaterThanOrEqualTo(2))));
    }

    @Test
    @Order(11)
    @DisplayName("GET /api/documents/{id} — get document by ID")
    void getDocumentById() throws Exception {
        Document doc = documentRepository.findAll().stream().findFirst().orElseThrow();
        mockMvc.perform(get("/api/documents/{id}", doc.getId()).with(jwt().jwt(testJwt())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(doc.getId().toString()))
                .andExpect(jsonPath("$.data.title").isNotEmpty());
    }

    @Test
    @Order(12)
    @DisplayName("POST /api/documents — create document with signers")
    void createDocument() throws Exception {
        Map<String, Object> payload = Map.of(
                "title", "Test Document via Integration Test",
                "description", "Created during smoke test",
                "fileName", "smoke-test.pdf",
                "contentType", "application/pdf",
                "fileContent", "JVBERi0xLjQK",
                "signers", List.of(
                        Map.of("email", "smoke-signer@test.com", "name", "Smoke Signer", "phone", "+33600000000")
                )
        );

        mockMvc.perform(post("/api/documents")
                        .with(jwt().jwt(testJwt()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.title").value("Test Document via Integration Test"))
                .andExpect(jsonPath("$.data.signers", hasSize(1)));
    }

    // ═══════════════════════════════════════════════════════════════════
    // SIGN (public endpoints — no JWT needed)
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @Order(20)
    @DisplayName("GET /api/sign/verify/{token} — verify signature token (public)")
    void verifySignatureToken() throws Exception {
        mockMvc.perform(get("/api/sign/verify/{token}", "test-token-signer-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value("signer1@example.com"))
                .andExpect(jsonPath("$.data.name").value("Jean Dupont"));
    }

    // ═══════════════════════════════════════════════════════════════════
    // PAYMENTS
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @Order(30)
    @DisplayName("GET /api/payments — lists seed payments")
    void listPayments() throws Exception {
        mockMvc.perform(get("/api/payments").with(jwt().jwt(testJwt())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content", hasSize(greaterThanOrEqualTo(2))));
    }

    @Test
    @Order(31)
    @DisplayName("GET /api/payments/{id} — get payment by ID")
    void getPaymentById() throws Exception {
        Payment payment = paymentRepository.findAll().stream().findFirst().orElseThrow();
        mockMvc.perform(get("/api/payments/{id}", payment.getId()).with(jwt().jwt(testJwt())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(payment.getId().toString()))
                .andExpect(jsonPath("$.data.amount").isNumber());
    }

    @Test
    @Order(32)
    @DisplayName("GET /api/payments/config — get Stripe publishable key")
    void getPaymentConfig() throws Exception {
        mockMvc.perform(get("/api/payments/config").with(jwt().jwt(testJwt())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.publishableKey").value("pk_test_placeholder"));
    }

    // ═══════════════════════════════════════════════════════════════════
    // ACCOUNT TYPES
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @Order(40)
    @DisplayName("GET /api/account-types — lists seed account types")
    void listAccountTypes() throws Exception {
        mockMvc.perform(get("/api/account-types").with(jwt().jwt(testJwt())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(3)))
                .andExpect(jsonPath("$.data[*].code",
                        containsInAnyOrder("CHECKING", "SAVINGS", "PREMIUM")));
    }

    // ═══════════════════════════════════════════════════════════════════
    // ACCOUNT APPLICATIONS
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @Order(50)
    @DisplayName("GET /api/account-applications — lists seed applications")
    void listAccountApplications() throws Exception {
        mockMvc.perform(get("/api/account-applications").with(jwt().jwt(testJwt())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content", hasSize(greaterThanOrEqualTo(1))));
    }

    @Test
    @Order(51)
    @DisplayName("GET /api/account-applications/{id} — get application by ID")
    void getAccountApplicationById() throws Exception {
        AccountApplication app = accountApplicationRepository.findAll().stream().findFirst().orElseThrow();
        mockMvc.perform(get("/api/account-applications/{id}", app.getId()).with(jwt().jwt(testJwt())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(app.getId().toString()))
                .andExpect(jsonPath("$.data.referenceNumber").isNotEmpty())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.kycDocuments", hasSize(greaterThanOrEqualTo(1))));
    }

    @Test
    @Order(52)
    @DisplayName("POST /api/account-applications — create new application")
    void createAccountApplication() throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("accountTypeCode", "SAVINGS");
        payload.put("dateOfBirth", "1992-07-14");
        payload.put("phoneNumber", "+33611111111");
        payload.put("nationality", "Française");
        payload.put("addressLine1", "1 rue Victor Hugo");
        payload.put("city", "Lyon");
        payload.put("postalCode", "69001");
        payload.put("country", "France");
        payload.put("employmentStatus", "EMPLOYED");
        payload.put("employerName", "Lyon Tech");
        payload.put("jobTitle", "Analyste");
        payload.put("monthlyIncome", 3200.00);

        mockMvc.perform(post("/api/account-applications")
                        .with(jwt().jwt(testJwt()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.accountType.code").value("SAVINGS"));
    }

    @Test
    @Order(53)
    @DisplayName("PUT /api/account-applications/{id} — update draft application")
    void updateAccountApplication() throws Exception {
        AccountApplication app = accountApplicationRepository.findAll().stream()
                .filter(a -> "ACC-20260403-TEST".equals(a.getReferenceNumber()))
                .findFirst().orElseThrow();

        Map<String, Object> payload = Map.of(
                "phoneNumber", "+33699999999",
                "city", "Marseille",
                "postalCode", "13001"
        );

        mockMvc.perform(put("/api/account-applications/{id}", app.getId())
                        .with(jwt().jwt(testJwt()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.city").value("Marseille"))
                .andExpect(jsonPath("$.data.postalCode").value("13001"));
    }

    // ═══════════════════════════════════════════════════════════════════
    // SECURITY SMOKE — confirm no 401/403 on protected endpoints
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @Order(99)
    @DisplayName("Verify no 401/403 on any endpoint — security fully disabled")
    void noAuthRequired() throws Exception {
        mockMvc.perform(get("/api/auth/me").with(jwt().jwt(testJwt()))).andExpect(status().isOk());
        mockMvc.perform(get("/api/documents").with(jwt().jwt(testJwt()))).andExpect(status().isOk());
        mockMvc.perform(get("/api/payments").with(jwt().jwt(testJwt()))).andExpect(status().isOk());
        mockMvc.perform(get("/api/payments/config").with(jwt().jwt(testJwt()))).andExpect(status().isOk());
        mockMvc.perform(get("/api/account-types").with(jwt().jwt(testJwt()))).andExpect(status().isOk());
        mockMvc.perform(get("/api/account-applications").with(jwt().jwt(testJwt()))).andExpect(status().isOk());
        // Public endpoints (no JWT)
        mockMvc.perform(get("/api/sign/verify/test-token-signer-1")).andExpect(status().isOk());
    }
}
