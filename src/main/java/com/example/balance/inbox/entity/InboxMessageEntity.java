package com.example.balance.inbox.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(
    name = "inbox_message",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_inbox_event_id",
        columnNames = "event_id"
    )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InboxMessageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, updatable = false, length = 100)
    private String eventId;

    @Column(nullable = false, length = 100)
    private String topic;

    @Column(nullable = false, updatable = false)
    private Instant processedAt;

    public InboxMessageEntity(String eventId, String topic) {
        this.eventId = eventId;
        this.topic = topic;
        this.processedAt = Instant.now();
    }
}
