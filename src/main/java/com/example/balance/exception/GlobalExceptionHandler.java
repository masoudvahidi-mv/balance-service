package com.example.balance.exception;

import jakarta.validation.ConstraintViolationException;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<ErrorResponse> accountNotFound(
            AccountNotFoundException ex
    ) {
        return response(
                HttpStatus.NOT_FOUND,
                "ACCOUNT_NOT_FOUND",
                ex.getMessage()
        );
    }

    @ExceptionHandler(InsufficientBalanceException.class)
    public ResponseEntity<ErrorResponse> insufficientBalance(
            InsufficientBalanceException ex
    ) {
        return response(
                HttpStatus.CONFLICT,
                "INSUFFICIENT_BALANCE",
                ex.getMessage()
        );
    }

    @ExceptionHandler({
            InvalidAmountException.class,
            SameAccountTransferException.class
    })
    public ResponseEntity<ErrorResponse> badRequest(
            RuntimeException ex
    ) {
        return response(
                HttpStatus.BAD_REQUEST,
                "INVALID_REQUEST",
                ex.getMessage()
        );
    }

    @ExceptionHandler(TransactionConflictException.class)
    public ResponseEntity<ErrorResponse> transactionConflict(
            TransactionConflictException ex
    ) {
        return response(
                HttpStatus.CONFLICT,
                "TRANSACTION_CONFLICT",
                ex.getMessage()
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> validation(
            MethodArgumentNotValidException ex
    ) {
        return response(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "Request validation failed"
        );
    }

    private ResponseEntity<ErrorResponse> response(
            HttpStatus status,
            String code,
            String message
    ) {
        return ResponseEntity.status(status).body(
                new ErrorResponse(
                        code,
                        message,
                        Instant.now()
                )
        );
    }

    public record ErrorResponse(
            String code,
            String message,
            Instant timestamp
    ) {}
}
