package com.example.balance.exception;

public class TransactionConflictException extends RuntimeException {
    public TransactionConflictException(String message) { super(message); }
}
