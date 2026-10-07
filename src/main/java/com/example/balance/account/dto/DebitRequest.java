package com.example.balance.account.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record DebitRequest(
        @NotBlank String accountId,
        @Positive long amount,
        @NotBlank String transactionId
) {
}
