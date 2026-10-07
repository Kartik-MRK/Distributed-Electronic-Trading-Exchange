package com.dete.simulator.dto;

import java.util.UUID;

public record DepositDto(UUID accountId, String asset, long amount, UUID referenceId) {}
