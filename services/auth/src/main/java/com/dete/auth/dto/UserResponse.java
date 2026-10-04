package com.dete.auth.dto;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(
    UUID userId, String username, String email, Instant createdAt, boolean isDemo) {}
