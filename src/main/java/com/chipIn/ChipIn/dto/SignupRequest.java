package com.chipIn.ChipIn.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString(exclude = {"password"})
public class SignupRequest {

    @NotBlank
    @Size(min = 1, max = 120)
    private String name;

    @Email
    @NotBlank
    private String email;

    @NotBlank
    @Size(min = 8, max = 128, message = "password must be 8-128 characters")
    private String password;

    @Pattern(regexp = "^[+0-9 ()-]{7,32}$", message = "invalid phone number")
    private String phone;

    private String invitationToken;
}
