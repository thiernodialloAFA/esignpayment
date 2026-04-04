package com.esign.payment.config;

import com.twilio.Twilio;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("!test")
@Getter
public class TwilioConfig {

    @Value("${twilio.account-sid}")
    private String accountSid;

    @Value("${twilio.auth-token}")
    private String authToken;

    @Value("${twilio.phone-number}")
    private String phoneNumber;

    @PostConstruct
    public void init() {
        if (accountSid != null && !accountSid.startsWith("AC_placeholder")) {
            Twilio.init(accountSid, authToken);
        }
    }
}
