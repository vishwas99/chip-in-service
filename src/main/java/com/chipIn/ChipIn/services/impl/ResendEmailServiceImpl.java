package com.chipIn.ChipIn.services.impl;

import com.chipIn.ChipIn.services.EmailService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * Sends invitation emails through the Resend HTTPS API
 * (https://resend.com/docs/api-reference/emails/send-email).
 *
 * Activated by setting {@code chipin.email.provider=resend} and supplying a
 * {@code resend.api-key}. Falls back to {@link MockEmailService} in any other
 * configuration so local development never accidentally hits the wire.
 *
 * Logging discipline:
 *   - We log recipient address, recipient name, and the resulting Resend
 *     {@code id} on success.
 *   - We NEVER log the invitation URL (it contains a single-use token).
 *   - On failure we log the HTTP status, response body up to 200 chars, and
 *     the recipient — but again, not the URL.
 */
@Service
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "chipin.email.provider", havingValue = "resend")
public class ResendEmailServiceImpl implements EmailService {

    private static final URI RESEND_ENDPOINT = URI.create("https://api.resend.com/emails");
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final ObjectMapper objectMapper;

    @Value("${resend.api-key}")
    private String apiKey;

    @Value("${chipin.email.from}")
    private String fromAddress;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .build();

    @Override
    public void sendInvitationEmail(String toEmail, String recipientName, String invitationLink) {
        if (apiKey == null || apiKey.isBlank()) {
            log.error("Resend email provider selected but resend.api-key is not configured; dropping invite to {}", toEmail);
            return;
        }
        if (toEmail == null || toEmail.isBlank()) {
            log.warn("Skipping invitation email: empty recipient address");
            return;
        }

        String safeName = recipientName == null || recipientName.isBlank() ? "there" : recipientName;
        String html = "<p>Hi " + escapeHtml(safeName) + ",</p>"
                + "<p>You've been invited to join ChipIn. Use the link in this email to set up your account "
                + "and start tracking expenses with your group.</p>"
                + "<p><a href=\"" + escapeHtml(invitationLink) + "\">Accept your invitation</a></p>"
                + "<p>The link expires in 24 hours.</p>";

        Map<String, Object> payload = Map.of(
                "from", fromAddress,
                "to", new String[]{toEmail},
                "subject", "You've been invited to ChipIn",
                "html", html
        );

        try {
            String body = objectMapper.writeValueAsString(payload);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(RESEND_ENDPOINT)
                    .timeout(TIMEOUT)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 == 2) {
                log.info("Sent invitation email to={} provider=resend status={}", toEmail, response.statusCode());
            } else {
                String snippet = response.body() == null ? "" :
                        response.body().substring(0, Math.min(200, response.body().length()));
                log.error("Resend email send failed to={} status={} body~={}", toEmail, response.statusCode(), snippet);
            }
        } catch (Exception e) {
            // Never propagate — invitation creation itself succeeded; the email
            // is best-effort and the user can re-invite.
            log.error("Resend email send threw to={} ex={}", toEmail, e.getClass().getSimpleName(), e);
        }
    }

    private static String escapeHtml(String s) {
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
