package com.example.balance.inbox.repository;

import com.example.balance.inbox.entity.BalanceEventAuditEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BalanceEventAuditRepository extends JpaRepository<BalanceEventAuditEntity, Long> {
}
