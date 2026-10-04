package com.dete.account.dto;

import java.util.UUID;

public record ReleaseFundsResponse(
    boolean success, String message, UUID accountId, String asset, long available, long reserved) {}
