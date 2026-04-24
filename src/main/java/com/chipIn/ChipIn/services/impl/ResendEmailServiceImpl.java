package com.chipIn.ChipIn.services.impl;

import com.chipIn.ChipIn.services.EmailService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class ResendEmailServiceImpl implements EmailService {

    @Value("${resend.apikey}")
    private String resendApiKey;

    @Value("${app.invitation.expiry.hours:24}")
    private int invitationExpiryHours;

    private static final String RESEND_API_URL = "https://api.resend.com/emails";
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void sendInvitationEmail(String toEmail, String recipientName, String invitationLink) {
        try {
            String htmlBody = "<div style=\"font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;\">"
                    + "<h3>Hello " + recipientName + ",</h3>"
                    + "<p>You have been invited to join <strong>ChipIn</strong>! Please click the button below to set up your account:</p>"
                    + "<p><a href=\"" + invitationLink + "\" style=\"background-color: #007bff; color: white; padding: 12px 30px; text-decoration: none; border-radius: 5px; display: inline-block;\">Activate your ChipIn account</a></p>"
                    + "<p>Or copy and paste this link in your browser:<br/><code>" + invitationLink + "</code></p>"
                    + "<p><strong>Note:</strong> This link will expire in " + invitationExpiryHours + " hours.</p>"
                    + "<hr style=\"border: none; border-top: 1px solid #ddd; margin: 20px 0;\">"
                    + "<p>Thank you,<br/><strong>The ChipIn Team</strong></p>"
                    + "</div>";

            // Create request body
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("from", "noreply@resend.dev");
            requestBody.put("to", toEmail);
            requestBody.put("subject", "You're invited to ChipIn!");
            requestBody.put("html", htmlBody);

            // Set up headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + resendApiKey);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            // Make the API call
            Map response = restTemplate.postForObject(RESEND_API_URL, request, Map.class);

            if (response != null && response.containsKey("id")) {
                log.info("Invitation email sent successfully to {} with message ID: {}", toEmail, response.get("id"));
            } else {
                log.error("Failed to send invitation email to {}: {}", toEmail, response);
                throw new RuntimeException("Failed to send invitation email: " + response);
            }

        } catch (RestClientException e) {
            log.error("Resend API error while sending email to {}: {}", toEmail, e.getMessage(), e);
            throw new RuntimeException("Failed to send invitation email via Resend", e);
        } catch (Exception e) {
            log.error("Unexpected error while sending email to {}: {}", toEmail, e.getMessage(), e);
            throw new RuntimeException("Failed to send invitation email", e);
        }
    }
}

