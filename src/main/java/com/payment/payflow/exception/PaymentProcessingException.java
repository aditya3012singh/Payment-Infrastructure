package com.payment.payflow.exception;

public class PaymentProcessingException extends RuntimeException {
    
    private final String errorCode;

    public PaymentProcessingException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
