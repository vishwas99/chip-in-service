package com.chipIn.ChipIn.services.impl;

import com.chipIn.ChipIn.services.EmailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class ResendEmailServiceImpl implements EmailService {

    @Override
    public void sendInvitationEmail(String toEmail, String recipientName, String invitationLink) {
        log.info("MOCK EMAIL SENT to: {} ({}) with invitation link: {}", toEmail, recipientName, invitationLink);
    }
}
