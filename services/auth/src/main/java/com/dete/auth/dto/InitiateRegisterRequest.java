package com.dete.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record InitiateRegisterRequest(
    @NotBlank(message = "Email is required") @Email(message = "Invalid email format") String email,
    @NotBlank(message = "Full name is required")
        @Size(min = 2, max = 128, message = "Full name must be between 2 and 128 characters")
        String fullName,
    LocalDate dateOfBirth,
    @NotBlank(message = "Password is required")
        @Size(min = 8, max = 100, message = "Password must be at least 8 characters")
        String password) {}
