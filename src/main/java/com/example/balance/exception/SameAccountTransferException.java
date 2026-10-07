package com.example.balance.exception;

public class SameAccountTransferException extends RuntimeException {
    public SameAccountTransferException(String message) { super(message); }
}
