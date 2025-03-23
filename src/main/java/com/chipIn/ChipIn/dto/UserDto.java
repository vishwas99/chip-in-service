package com.chipIn.ChipIn.dto;

import com.chipIn.ChipIn.entities.User;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Data
@Getter
@Setter
public class UserDto {

    @NotBlank(message = "Name cannot be empty")
    @Size(min = 2, max = 20, message = "Name must be between 2-100 characters")
    private String name;

    @NotBlank(message = "Email cannot be empty")
    @Pattern(regexp = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$", message = "Invalid email format")
    private String email;

    @Pattern(regexp = "^[0-9]{10}$", message = "Invalid phone number format")
    private String phoneNumber;

    private LocalDateTime createdAt;

    public User toEntity() {
        User user = new User();
        user.setName(this.name);
        user.setEmail(this.email);
        user.setPhone(this.phoneNumber);
        user.setCreatedAt(this.createdAt);
        return user;
    }

}
