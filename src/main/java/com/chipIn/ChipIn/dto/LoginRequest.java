package com.chipIn.ChipIn.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {

    @Email
    @NotBlank
    private String email;

    @NotBlank
    private String password;

    /**
     * Intentionally does NOT include the password. Logging this DTO is safe.
     */
    @Override
    public String toString() {
        return "LoginRequest(email=" + email + ")";
    }
}
