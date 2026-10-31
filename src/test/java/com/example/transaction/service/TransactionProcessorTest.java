package com.example.transaction.service;

import com.example.transaction.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TransactionProcessorTest {
    private TransactionProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new TransactionProcessor(new TransactionValidator(), 3);
    }

    @Test
    void processesOutOfOrderEventsWhenMissingSequenceArrives() {
        TransactionRequest second = request("TXN-2", "REQ-2", 2, "ACC-1",
                TransactionType.DEBIT, "40", false);
        TransactionRequest first = request("TXN-1", "REQ-1", 1, "ACC-1",
                TransactionType.CREDIT, "100", false);

        processor.processBatch(List.of(second));
        assertEquals(TransactionStatus.RETRY_PENDING,
                processor.findTransaction("TXN-2").orElseThrow().getStatus());
        processor.processBatch(List.of(first));

        assertEquals(TransactionStatus.PROCESSED,
                processor.findTransaction("TXN-1").orElseThrow().getStatus());
        assertEquals(TransactionStatus.PROCESSED,
                processor.findTransaction("TXN-2").orElseThrow().getStatus());
        assertEquals(new BigDecimal("60"), processor.getBalance("ACC-1"));
    }

    @Test
    void duplicateBusinessTransactionDoesNotChangeBalanceTwice() {
        TransactionRequest original = request("TXN-1", "REQ-1", 1, "ACC-1",
                TransactionType.CREDIT, "100", false);
        TransactionRequest duplicate = request("TXN-1", "REQ-1-DUP", 1, "ACC-1",
                TransactionType.CREDIT, "100", false);

        processor.processBatch(List.of(original));
        ProcessingResult duplicateResult = processor.processBatch(List.of(duplicate)).get(0);

        assertEquals(TransactionStatus.DUPLICATE, duplicateResult.status());
        assertEquals(new BigDecimal("100"), processor.getBalance("ACC-1"));
    }

    @Test
    void invalidRecordIsRejectedWithoutStoppingBatch() {
        TransactionRequest invalid = request("TXN-BAD", "REQ-BAD", 1, "ACC-1",
                TransactionType.DEBIT, "-10", false);
        TransactionRequest valid = request("TXN-GOOD", "REQ-GOOD", 2, "ACC-1",
                TransactionType.CREDIT, "50", false);

        List<ProcessingResult> results = processor.processBatch(List.of(invalid, valid));

        assertEquals(TransactionStatus.FAILED, results.get(0).status());
        assertEquals(TransactionStatus.PROCESSED, results.get(1).status());
        assertEquals(new BigDecimal("50"), processor.getBalance("ACC-1"));
    }

    @Test
    void retriesTransientFailureWithoutDuplicateEffect() {
        TransactionRequest retryable = request("TXN-RETRY", "REQ-RETRY", 1, "ACC-1",
                TransactionType.CREDIT, "75", true);

        ProcessingResult result = processor.processBatch(List.of(retryable)).get(0);

        assertEquals(TransactionStatus.PROCESSED, result.status());
        assertEquals(2, result.attempts());
        assertEquals(new BigDecimal("75"), processor.getBalance("ACC-1"));
    }

    @Test
    void insufficientFundsFailsAndDoesNotChangeBalance() {
        TransactionRequest debit = request("TXN-DEBIT", "REQ-DEBIT", 1, "ACC-1",
                TransactionType.DEBIT, "25", false);

        ProcessingResult result = processor.processBatch(List.of(debit)).get(0);

        assertEquals(TransactionStatus.FAILED, result.status());
        assertEquals(BigDecimal.ZERO, processor.getBalance("ACC-1"));
    }

    private TransactionRequest request(String id, String requestId, long sequence,
                                       String account, TransactionType type,
                                       String amount, boolean failFirstAttempt) {
        return new TransactionRequest(id, requestId, sequence, account, type,
                new BigDecimal(amount), "INR", "2026-10-31T10:00:00Z", failFirstAttempt);
    }
}
