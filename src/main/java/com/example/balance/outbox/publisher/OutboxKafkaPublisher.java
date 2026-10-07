package com.example.balance.outbox.publisher;

import com.example.balance.outbox.entity.OutboxEventEntity;
import com.example.balance.outbox.entity.OutboxStatus;
import com.example.balance.outbox.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxKafkaPublisher {

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Value("${balance.kafka.topic}")
    private String topic;

    @Value("${balance.outbox.batch-size:100}")
    private int batchSize;

    @Scheduled(fixedDelayString = "${balance.outbox.fixed-delay:1000}")
    public void publish() {
        List<OutboxEventEntity> events = findPending();

        for (OutboxEventEntity event : events) {
            try {
                kafkaTemplate.send(
                        topic,
                        event.getAggregateId(),
                        event.getPayload()
                ).get();

                markPublished(event.getId());

            } catch (Exception e) {
                log.error(
                        "Failed to publish outbox event {}",
                        event.getEventId(),
                        e
                );
            }
        }
    }

    @Transactional(readOnly = true)
    protected List<OutboxEventEntity> findPending() {
        return outboxRepository.findByStatusOrderByCreatedAtAsc(
                OutboxStatus.PENDING,
                PageRequest.of(0, batchSize)
        );
    }

    @Transactional
    protected void markPublished(Long id) {
        outboxRepository.findById(id)
                .ifPresent(OutboxEventEntity::markPublished);
    }
}
