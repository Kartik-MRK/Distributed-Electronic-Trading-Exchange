package com.dete.order.dto;

import jakarta.validation.constraints.Positive;

public record ModifyOrderRequest(
    @Positive(message = "New price must be strictly positive") Long newPrice,
    @Positive(message = "New quantity must be strictly positive") long newQuantity) {}
