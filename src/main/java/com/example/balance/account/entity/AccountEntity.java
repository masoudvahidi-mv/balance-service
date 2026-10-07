package com.example.balance.account.entity;

import com.example.balance.exception.InsufficientBalanceException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "account")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccountEntity {

    @Id
    @Column(nullable = false, updatable = false, length = 100)
    private String id;

    @Column(nullable = false)
    private long balance;

    public AccountEntity(String id, long balance) {
        this.id = id;
        this.balance = balance;
    }

    public void credit(long amount) {
        this.balance = Math.addExact(this.balance, amount);
    }

    public void debit(long amount) {
        if (this.balance < amount) {
            throw new InsufficientBalanceException("Insufficient balance for account: " + id);
        }
        this.balance = Math.subtractExact(this.balance, amount);
    }
}
