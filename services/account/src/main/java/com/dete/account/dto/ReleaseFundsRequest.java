package com.dete.account.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.UUID;

public record ReleaseFundsRequest(
    @NotNull(message = "Account ID is required") UUID accountId,
    @NotNull(message = "Order ID is required") UUID orderId,
    @NotBlank(message = "Asset symbol is required") String asset,
    @Positive(message = "Release amount must be positive") long amount) {}
