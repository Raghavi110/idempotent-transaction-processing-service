package com.example.transaction.service;

import com.example.transaction.model.TransactionRequest;

import java.math.BigDecimal;
import java.util.Optional;

public class TransactionValidator {

    public Optional<String> validate(TransactionRequest request) {
        if (request == null) return Optional.of("Request cannot be null");
        if (isBlank(request.transactionId())) return Optional.of("transactionId is required");
        if (isBlank(request.requestId())) return Optional.of("requestId is required");
        if (isBlank(request.accountId())) return Optional.of("accountId is required");
        if (request.sequenceNumber() <= 0) return Optional.of("sequenceNumber must be greater than zero");
        if (request.transactionType() == null) return Optional.of("transactionType is required");
        if (request.amount() == null || request.amount().compareTo(BigDecimal.ZERO) <= 0)
            return Optional.of("amount must be greater than zero");
        if (isBlank(request.currency()) || !request.currency().matches("[A-Z]{3}"))
            return Optional.of("currency must be a 3-letter uppercase code");
        return Optional.empty();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
