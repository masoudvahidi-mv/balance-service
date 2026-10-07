package com.example.balance.transaction.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(
    name = "financial_transaction",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_financial_transaction_transaction_id",
        columnNames = "transaction_id"
    )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TransactionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transaction_id", nullable = false, updatable = false, length = 100)
    private String transactionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionType type;

    @Column(nullable = false)
    private long amount;

    @Column(name = "source_account_id", length = 100)
    private String sourceAccountId;

    @Column(name = "destination_account_id", length = 100)
    private String destinationAccountId;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public TransactionEntity(
            String transactionId,
            TransactionType type,
            long amount,
            String sourceAccountId,
            String destinationAccountId
    ) {
        this.transactionId = transactionId;
        this.type = type;
        this.amount = amount;
        this.sourceAccountId = sourceAccountId;
        this.destinationAccountId = destinationAccountId;
        this.createdAt = Instant.now();
    }

    public boolean matches(
            TransactionType requestedType,
            long requestedAmount,
            String requestedSource,
            String requestedDestination
    ) {
        return type == requestedType
                && amount == requestedAmount
                && Objects.equals(sourceAccountId, requestedSource)
                && Objects.equals(destinationAccountId, requestedDestination);
    }
}
