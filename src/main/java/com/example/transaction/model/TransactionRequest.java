package com.example.transaction.model;

import java.math.BigDecimal;

/**
 * Input event. failFirstAttempt is a test/demo switch used to simulate a transient error.
 */
public record TransactionRequest(
        String transactionId,
        String requestId,
        long sequenceNumber,
        String accountId,
        TransactionType transactionType,
        BigDecimal amount,
        String currency,
        String eventTime,
        boolean failFirstAttempt
) {
}
