package com.example.balance.redis;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class RedisIdempotencyService {

    private static final String PREFIX = "balance:transaction:";

    private final StringRedisTemplate redisTemplate;

    @Value("${balance.redis.ttl-hours:24}")
    private long ttlHours;

    public boolean isProcessed(String transactionId) {
        return Boolean.TRUE.equals(
                redisTemplate.hasKey(PREFIX + transactionId)
        );
    }

    public void markProcessed(String transactionId) {
        redisTemplate.opsForValue().set(
                PREFIX + transactionId,
                "1",
                Duration.ofHours(ttlHours)
        );
    }
}
