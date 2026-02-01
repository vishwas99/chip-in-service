package com.chipIn.ChipIn.dto;

import lombok.Data;

@Data
public class AddMemberRequest {
    private String email; // The email of the user to be added
    private boolean isAdmin = false; // Optional: Can add them as admin directly
}
