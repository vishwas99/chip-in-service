package com.chipIn.ChipIn.services;

import com.chipIn.ChipIn.dto.InviteRequest;
import com.chipIn.ChipIn.entities.User;

public interface InvitationService {
    User inviteUser(InviteRequest inviteRequest);
    void sendInvitationEmail(User invitedUser, String invitationLink);
    User registerInvitedUser(String token, String password);
}
