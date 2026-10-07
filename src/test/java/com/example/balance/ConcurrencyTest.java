package com.example.balance;

import com.example.balance.account.entity.AccountEntity;
import com.example.balance.account.repository.AccountRepository;
import com.example.balance.account.service.BalanceService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConcurrencyTest extends BasePostgresTest {

    @Autowired
    BalanceService balanceService;

    @Autowired
    AccountRepository accountRepository;

    @BeforeEach
    void setup() {
        accountRepository.deleteAll();
    }

    @Test
    void concurrentDebitsMustNotCreateNegativeBalance()
            throws Exception {

        accountRepository.save(
                new AccountEntity("A", 100_000)
        );

        int threads = 100;

        ExecutorService executor =
                Executors.newFixedThreadPool(20);

        CountDownLatch start =
                new CountDownLatch(1);

        List<Future<Boolean>> futures =
                new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            String txId = "TX-DEBIT-" + i;

            futures.add(
                    executor.submit(() -> {
                        start.await();
                        try {
                            balanceService.debit(
                                    "A",
                                    1000,
                                    txId
                            );
                            return true;
                        } catch (Exception e) {
                            return false;
                        }
                    })
            );
        }

        start.countDown();

        int success = 0;

        for (Future<Boolean> future : futures) {
            if (future.get(30, TimeUnit.SECONDS)) {
                success++;
            }
        }

        executor.shutdown();

        assertEquals(100, success);
        assertEquals(0, balanceService.getBalance("A"));
    }

    @Test
    void concurrentDuplicateRequestsMustHaveOneFinancialEffect()
            throws Exception {

        accountRepository.save(
                new AccountEntity("A", 1000)
        );

        int threads = 50;

        ExecutorService executor =
                Executors.newFixedThreadPool(20);

        CountDownLatch start =
                new CountDownLatch(1);

        List<Future<?>> futures =
                new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            futures.add(
                    executor.submit(() -> {
                        start.await();
                        balanceService.credit(
                                "A",
                                100,
                                "SAME-TX"
                        );
                        return null;
                    })
            );
        }

        start.countDown();

        for (Future<?> future : futures) {
            future.get(30, TimeUnit.SECONDS);
        }

        executor.shutdown();

        assertEquals(1100, balanceService.getBalance("A"));
    }

    @Test
    void concurrentOppositeTransfersMustPreserveTotal()
            throws Exception {

        accountRepository.save(
                new AccountEntity("A", 100_000)
        );

        accountRepository.save(
                new AccountEntity("B", 100_000)
        );

        int threads = 100;

        ExecutorService executor =
                Executors.newFixedThreadPool(20);

        CountDownLatch start =
                new CountDownLatch(1);

        List<Future<?>> futures =
                new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            final int index = i;

            futures.add(
                    executor.submit(() -> {
                        start.await();

                        if (index % 2 == 0) {
                            balanceService.transfer(
                                    "A",
                                    "B",
                                    1000,
                                    "TX-A-" + index
                            );
                        } else {
                            balanceService.transfer(
                                    "B",
                                    "A",
                                    1000,
                                    "TX-B-" + index
                            );
                        }

                        return null;
                    })
            );
        }

        start.countDown();

        for (Future<?> future : futures) {
            future.get(30, TimeUnit.SECONDS);
        }

        executor.shutdown();

        long a = balanceService.getBalance("A");
        long b = balanceService.getBalance("B");

        assertEquals(200_000, a + b);
    }
}
