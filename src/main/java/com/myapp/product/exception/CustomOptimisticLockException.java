package com.myapp.product.exception;

public class CustomOptimisticLockException extends RuntimeException {
    public CustomOptimisticLockException(String message) {
        super(message);
    }
}

