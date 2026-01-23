package com.chipIn.ChipIn.dto;

import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@Builder
public class LoginResponse {
    private String token;
    private String name;
    private String email;
}
