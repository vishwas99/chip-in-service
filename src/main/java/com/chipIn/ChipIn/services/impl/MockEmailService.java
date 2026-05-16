package com.chipIn.ChipIn.services.impl;

import com.chipIn.ChipIn.services.EmailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Default {@link EmailService} used when {@code chipin.email.provider} is unset
 * or {@code mock}. Records that an invitation would have been sent without
 * making any network call or logging the invitation URL.
 *
 * The invitation URL contains a single-use, high-entropy token; we deliberately
 * log only the recipient address.
 */
@Service
@Slf4j
@ConditionalOnProperty(name = "chipin.email.provider", havingValue = "mock", matchIfMissing = true)
public class MockEmailService implements EmailService {

    @Override
    public void sendInvitationEmail(String toEmail, String recipientName, String invitationLink) {
        log.info("Mock email provider — would have sent invitation to={} name={}", toEmail, recipientName);
    }
}
