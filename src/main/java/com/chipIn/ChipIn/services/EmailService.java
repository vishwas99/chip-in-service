package com.chipIn.ChipIn.services;

public interface EmailService {
    void sendInvitationEmail(String toEmail, String recipientName, String invitationLink);
}

