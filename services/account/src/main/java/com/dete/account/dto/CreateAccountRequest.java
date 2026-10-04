package com.dete.account.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateAccountRequest(@NotNull(message = "Account ID is required") UUID accountId) {}
