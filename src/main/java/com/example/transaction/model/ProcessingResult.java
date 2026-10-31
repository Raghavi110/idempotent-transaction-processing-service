package com.example.transaction.model;

public record ProcessingResult(
        String transactionId,
        String requestId,
        TransactionStatus status,
        int attempts,
        String message
) {
    public static ProcessingResult from(TransactionRecord record) {
        return new ProcessingResult(
                record.getRequest().transactionId(),
                record.getRequest().requestId(),
                record.getStatus(),
                record.getAttempts(),
                record.getMessage()
        );
    }
}
