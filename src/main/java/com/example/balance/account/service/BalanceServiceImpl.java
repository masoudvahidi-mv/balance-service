package com.example.balance.account.service;

import com.example.balance.account.entity.AccountEntity;
import com.example.balance.account.repository.AccountRepository;
import com.example.balance.exception.*;
import com.example.balance.kafka.BalanceEvent;
import com.example.balance.outbox.entity.OutboxEventEntity;
import com.example.balance.outbox.repository.OutboxRepository;
import com.example.balance.redis.RedisIdempotencyService;
import com.example.balance.redis.RedisTransactionCache;
import com.example.balance.transaction.entity.TransactionEntity;
import com.example.balance.transaction.entity.TransactionType;
import com.example.balance.transaction.repository.TransactionLockRepository;
import com.example.balance.transaction.repository.TransactionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BalanceServiceImpl implements BalanceService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final TransactionLockRepository transactionLockRepository;
    private final OutboxRepository outboxRepository;
    private final RedisIdempotencyService redisIdempotencyService;
    private final RedisTransactionCache redisTransactionCache;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void credit(String accountId, long amount, String transactionId) {
        validateAmount(amount);
        validateTransactionId(transactionId);

        transactionLockRepository.lock(transactionId);

        Optional<TransactionEntity> existing =
                transactionRepository.findByTransactionId(transactionId);

        if (existing.isPresent()) {
            validateExisting(existing.get(), TransactionType.CREDIT, amount, accountId, null);
            redisTransactionCache.markAfterCommit(transactionId);
            return;
        }

        AccountEntity account = lockAccount(accountId);
        account.credit(amount);

        TransactionEntity transaction = new TransactionEntity(
                transactionId,
                TransactionType.CREDIT,
                amount,
                accountId,
                null
        );
        transactionRepository.save(transaction);

        saveOutbox(transaction, accountId, null, amount);
        redisTransactionCache.markAfterCommit(transactionId);
    }

    @Override
    @Transactional
    public void debit(String accountId, long amount, String transactionId) {
        validateAmount(amount);
        validateTransactionId(transactionId);

        transactionLockRepository.lock(transactionId);

        Optional<TransactionEntity> existing =
                transactionRepository.findByTransactionId(transactionId);

        if (existing.isPresent()) {
            validateExisting(existing.get(), TransactionType.DEBIT, amount, accountId, null);
            redisTransactionCache.markAfterCommit(transactionId);
            return;
        }

        AccountEntity account = lockAccount(accountId);
        account.debit(amount);

        TransactionEntity transaction = new TransactionEntity(
                transactionId,
                TransactionType.DEBIT,
                amount,
                accountId,
                null
        );
        transactionRepository.save(transaction);

        saveOutbox(transaction, accountId, null, amount);
        redisTransactionCache.markAfterCommit(transactionId);
    }

    @Override
    @Transactional
    public void transfer(
            String sourceAccountId,
            String destinationAccountId,
            long amount,
            String transactionId
    ) {
        validateAmount(amount);
        validateTransactionId(transactionId);

        if (sourceAccountId.equals(destinationAccountId)) {
            throw new SameAccountTransferException(
                    "Source and destination accounts must be different"
            );
        }

        transactionLockRepository.lock(transactionId);

        Optional<TransactionEntity> existing =
                transactionRepository.findByTransactionId(transactionId);

        if (existing.isPresent()) {
            validateExisting(
                    existing.get(),
                    TransactionType.TRANSFER,
                    amount,
                    sourceAccountId,
                    destinationAccountId
            );
            redisTransactionCache.markAfterCommit(transactionId);
            return;
        }
        List<String> orderedIds = List.of(
                sourceAccountId,
                destinationAccountId
        ).stream().sorted().toList();

        AccountEntity first = lockAccount(orderedIds.get(0));
        AccountEntity second = lockAccount(orderedIds.get(1));

        AccountEntity source =
                sourceAccountId.equals(first.getId()) ? first : second;

        AccountEntity destination =
                destinationAccountId.equals(first.getId()) ? first : second;

        source.debit(amount);
        destination.credit(amount);

        TransactionEntity transaction = new TransactionEntity(
                transactionId,
                TransactionType.TRANSFER,
                amount,
                sourceAccountId,
                destinationAccountId
        );
        transactionRepository.save(transaction);

        saveOutbox(
                transaction,
                sourceAccountId,
                destinationAccountId,
                amount
        );

        redisTransactionCache.markAfterCommit(transactionId);
    }

    @Override
    @Transactional(readOnly = true)
    public long getBalance(String accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() ->
                        new AccountNotFoundException(
                                "Account not found: " + accountId
                        )
                )
                .getBalance();
    }

    private AccountEntity lockAccount(String accountId) {
        return accountRepository.findByIdForUpdate(accountId)
                .orElseThrow(() ->
                        new AccountNotFoundException(
                                "Account not found: " + accountId
                        )
                );
    }

    private void validateAmount(long amount) {
        if (amount <= 0) {
            throw new InvalidAmountException(
                    "Amount must be greater than zero"
            );
        }
    }

    private void validateTransactionId(String transactionId) {
        if (transactionId == null || transactionId.isBlank()) {
            throw new InvalidAmountException(
                    "Transaction ID must not be blank"
            );
        }
    }

    private void validateExisting(
            TransactionEntity transaction,
            TransactionType type,
            long amount,
            String source,
            String destination
    ) {
        if (!transaction.matches(type, amount, source, destination)) {
            throw new TransactionConflictException(
                    "Transaction ID already exists with different transaction data: "
                            + transaction.getTransactionId()
            );
        }
    }

    private void saveOutbox(
            TransactionEntity transaction,
            String sourceAccountId,
            String destinationAccountId,
            long amount
    ) {
        BalanceEvent event = new BalanceEvent(
                UUID.randomUUID().toString(),
                transaction.getTransactionId(),
                transaction.getType().name(),
                sourceAccountId,
                destinationAccountId,
                amount
        );

        try {
            String payload = objectMapper.writeValueAsString(event);

            outboxRepository.save(
                    new OutboxEventEntity(
                            event.eventId(),
                            "ACCOUNT",
                            sourceAccountId,
                            transaction.getType().name(),
                            payload
                    )
            );
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "Cannot serialize balance event",
                    e
            );
        }
    }
}
