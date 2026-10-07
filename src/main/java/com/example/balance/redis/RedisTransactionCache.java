package com.example.balance.redis;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
public class RedisTransactionCache {

    private final RedisIdempotencyService redis;

    public void markAfterCommit(String transactionId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            redis.markProcessed(transactionId);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        redis.markProcessed(transactionId);
                    }
                }
        );
    }
}
