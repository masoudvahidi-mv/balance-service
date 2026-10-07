package com.example.balance;

import com.example.balance.account.entity.AccountEntity;
import com.example.balance.account.repository.AccountRepository;
import com.example.balance.account.service.BalanceService;
import com.example.balance.exception.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.*;

class BalanceServiceTest extends BasePostgresTest {

    @Autowired
    BalanceService balanceService;

    @Autowired
    AccountRepository accountRepository;

    @BeforeEach
    void setup() {
        accountRepository.deleteAll();
        accountRepository.save(new AccountEntity("A", 1000));
        accountRepository.save(new AccountEntity("B", 500));
    }

    @Test
    void creditShouldIncreaseBalance() {
        balanceService.credit("A", 500, "TX-1");
        assertEquals(1500, balanceService.getBalance("A"));
    }

    @Test
    void debitShouldDecreaseBalance() {
        balanceService.debit("A", 700, "TX-1");
        assertEquals(300, balanceService.getBalance("A"));
    }

    @Test
    void debitShouldFailWhenBalanceIsInsufficient() {
        assertThrows(
                InsufficientBalanceException.class,
                () -> balanceService.debit("A", 1200, "TX-1")
        );

        assertEquals(1000, balanceService.getBalance("A"));
    }

    @Test
    void transferShouldBeAtomic() {
        balanceService.transfer("A", "B", 300, "TX-1");

        assertEquals(700, balanceService.getBalance("A"));
        assertEquals(800, balanceService.getBalance("B"));
    }

    @Test
    void sameAccountTransferShouldFail() {
        assertThrows(
                SameAccountTransferException.class,
                () -> balanceService.transfer("A", "A", 100, "TX-1")
        );

        assertEquals(1000, balanceService.getBalance("A"));
    }

    @Test
    void sameTransactionShouldBeAppliedOnlyOnce() {
        balanceService.credit("A", 100, "TX-1");
        balanceService.credit("A", 100, "TX-1");
        balanceService.credit("A", 100, "TX-1");

        assertEquals(1100, balanceService.getBalance("A"));
    }

    @Test
    void sameTransactionWithDifferentPayloadShouldFail() {
        balanceService.credit("A", 100, "TX-1");

        assertThrows(
                TransactionConflictException.class,
                () -> balanceService.credit("A", 200, "TX-1")
        );

        assertEquals(1100, balanceService.getBalance("A"));
    }

    @Test
    void transferShouldBeIdempotent() {
        balanceService.transfer("A", "B", 300, "TX-1");
        balanceService.transfer("A", "B", 300, "TX-1");

        assertEquals(700, balanceService.getBalance("A"));
        assertEquals(800, balanceService.getBalance("B"));
    }
}
