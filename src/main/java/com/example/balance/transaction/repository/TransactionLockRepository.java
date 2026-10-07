package com.example.balance.transaction.repository;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class TransactionLockRepository {

    private final EntityManager entityManager;

    /*
     * PostgreSQL advisory transaction lock.
     *
     * This serializes concurrent requests using the same transactionId,
     * even before a Transaction row exists.
     *
     * pg_advisory_xact_lock(hashtextextended(...)) is released automatically
     * when the surrounding database transaction commits or rolls back.
     */
    public void lock(String transactionId) {
        entityManager.createNativeQuery(
                "select pg_advisory_xact_lock(hashtextextended(:transactionId, 0))"
        ).setParameter("transactionId", transactionId)
         .getSingleResult();
    }
}
