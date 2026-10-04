package com.dete.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
    @NotBlank(message = "Username must not be blank")
        @Size(min = 3, max = 64, message = "Username must be between 3 and 64 characters")
        String username,
    @NotBlank(message = "Email must not be blank") @Email(message = "Invalid email format")
        String email,
    @NotBlank(message = "Password must not be blank")
        @Size(min = 8, max = 128, message = "Password must be at least 8 characters")
        String password) {}
