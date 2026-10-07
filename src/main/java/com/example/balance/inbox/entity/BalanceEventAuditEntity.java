package com.example.balance.inbox.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(
    name = "balance_event_audit",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_balance_event_audit_event_id",
        columnNames = "event_id"
    )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BalanceEventAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, updatable = false, length = 100)
    private String eventId;

    @Column(name = "transaction_id", nullable = false, length = 100)
    private String transactionId;

    @Column(nullable = false, length = 50)
    private String eventType;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public BalanceEventAuditEntity(
            String eventId,
            String transactionId,
            String eventType
    ) {
        this.eventId = eventId;
        this.transactionId = transactionId;
        this.eventType = eventType;
        this.createdAt = Instant.now();
    }
}
