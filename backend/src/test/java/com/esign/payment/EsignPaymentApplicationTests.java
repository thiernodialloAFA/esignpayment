package com.esign.payment;

import com.esign.payment.config.TestDataInitializer;
import com.esign.payment.config.TestSecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import({TestSecurityConfig.class, TestDataInitializer.class})
class EsignPaymentApplicationTests {

    @Test
    void contextLoads() {
    }
}
