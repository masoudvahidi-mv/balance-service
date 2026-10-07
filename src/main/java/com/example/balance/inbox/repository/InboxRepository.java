package com.example.balance.inbox.repository;

import com.example.balance.inbox.entity.InboxMessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InboxRepository extends JpaRepository<InboxMessageEntity, Long> {
    Optional<InboxMessageEntity> findByEventId(String eventId);
}
