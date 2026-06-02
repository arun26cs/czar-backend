package com.czar.auth.service;

import com.sendgrid.Method;
import com.sendgrid.Request;
import com.sendgrid.SendGrid;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final String sendGridApiKey;
    private final String fromAddress;

    public EmailService(
            @Value("${sendgrid.api-key:}") String sendGridApiKey,
            @Value("${sendgrid.from-address:noreply@czar.app}") String fromAddress) {
        this.sendGridApiKey = sendGridApiKey;
        this.fromAddress = fromAddress;
    }

    /**
     * Sends an OTP email via SendGrid.
     * Falls back to console logging when SENDGRID_API_KEY is not set (local dev).
     */
    public void sendOtp(String toEmail, String otp) {
        if (sendGridApiKey == null || sendGridApiKey.isBlank()) {
            log.info("=== [DEV MODE] OTP for {} : {} ===", toEmail, otp);
            return;
        }

        try {
            Mail mail = new Mail(
                    new Email(fromAddress),
                    "Your Czar verification code",
                    new Email(toEmail),
                    new Content("text/plain",
                            "Your Czar verification code is: " + otp + "\n\nThis code expires in 10 minutes.")
            );

            SendGrid sg = new SendGrid(sendGridApiKey);
            Request request = new Request();
            request.setMethod(Method.POST);
            request.setEndpoint("mail/send");
            request.setBody(mail.build());

            var response = sg.api(request);
            if (response.getStatusCode() >= 400) {
                log.error("SendGrid error {}: {}", response.getStatusCode(), response.getBody());
                throw new RuntimeException("Failed to send OTP email");
            }
            log.info("OTP email sent to {}", toEmail);
        } catch (Exception e) {
            log.error("Email send failed for {}: {}", toEmail, e.getMessage());
            throw new RuntimeException("Failed to send OTP email", e);
        }
    }
}
