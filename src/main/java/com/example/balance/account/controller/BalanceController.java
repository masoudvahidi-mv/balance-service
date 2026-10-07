package com.example.balance.account.controller;

import com.example.balance.account.dto.*;
import com.example.balance.account.service.BalanceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/balances")
@RequiredArgsConstructor
public class BalanceController {

    private final BalanceService balanceService;

    @PostMapping("/credit")
    public ResponseEntity<Void> credit(
            @Valid @RequestBody CreditRequest request
    ) {
        balanceService.credit(
                request.accountId(),
                request.amount(),
                request.transactionId()
        );
        return ResponseEntity.ok().build();
    }

    @PostMapping("/debit")
    public ResponseEntity<Void> debit(
            @Valid @RequestBody DebitRequest request
    ) {
        balanceService.debit(
                request.accountId(),
                request.amount(),
                request.transactionId()
        );
        return ResponseEntity.ok().build();
    }

    @PostMapping("/transfer")
    public ResponseEntity<Void> transfer(
            @Valid @RequestBody TransferRequest request
    ) {
        balanceService.transfer(
                request.sourceAccountId(),
                request.destinationAccountId(),
                request.amount(),
                request.transactionId()
        );
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{accountId}")
    public ResponseEntity<BalanceResponse> getBalance(
            @PathVariable String accountId
    ) {
        return ResponseEntity.ok(
                new BalanceResponse(
                        accountId,
                        balanceService.getBalance(accountId)
                )
        );
    }
}
