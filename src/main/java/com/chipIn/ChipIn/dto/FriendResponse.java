package com.chipIn.ChipIn.dto;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class FriendResponse {
    private UUID userId;
    private String name;
    private String email;
    private String profilePicUrl;
}
