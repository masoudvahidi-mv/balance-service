package com.example.balance.kafka;

import com.example.balance.inbox.entity.BalanceEventAuditEntity;
import com.example.balance.inbox.entity.InboxMessageEntity;
import com.example.balance.inbox.repository.BalanceEventAuditRepository;
import com.example.balance.inbox.repository.InboxRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class BalanceEventConsumer {

    private final InboxRepository inboxRepository;
    private final BalanceEventAuditRepository auditRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "${balance.kafka.topic}",
            groupId = "balance-audit-consumer"
    )
    @Transactional
    public void consume(String payload) {
        try {
            BalanceEvent event =
                    objectMapper.readValue(payload, BalanceEvent.class);

            if (inboxRepository.findByEventId(event.eventId()).isPresent()) {
                return;
            }

            auditRepository.save(
                    new BalanceEventAuditEntity(
                            event.eventId(),
                            event.transactionId(),
                            event.type()
                    )
            );

            try {
                inboxRepository.saveAndFlush(
                        new InboxMessageEntity(
                                event.eventId(),
                                "balance.events"
                        )
                );
            } catch (DataIntegrityViolationException duplicate) {
                log.debug(
                        "Duplicate inbox event ignored: {}",
                        event.eventId()
                );
            }

        } catch (Exception e) {
            throw new IllegalStateException(
                    "Cannot process balance event",
                    e
            );
        }
    }
}
