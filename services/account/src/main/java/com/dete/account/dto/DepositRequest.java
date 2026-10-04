package com.dete.account.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.UUID;

public record DepositRequest(
    @NotNull(message = "Account ID is required") UUID accountId,
    @NotBlank(message = "Asset symbol is required") String asset,
    @Positive(message = "Deposit amount must be positive") long amount,
    UUID referenceId) {}
