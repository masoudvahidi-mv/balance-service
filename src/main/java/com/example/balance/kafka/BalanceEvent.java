package com.example.balance.kafka;

public record BalanceEvent(
        String eventId,
        String transactionId,
        String type,
        String sourceAccountId,
        String destinationAccountId,
        long amount
) {
}
