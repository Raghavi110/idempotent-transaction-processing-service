package com.example.transaction.service;

import com.example.transaction.model.*;

import java.math.BigDecimal;
import java.util.*;
import java.util.logging.Logger;

/**
 * In-memory reference implementation.
 *
 * Ordering assumption for this exercise: sequenceNumber is a single ordered input stream
 * (not an independent counter per account). The next expected sequence starts at 1.
 */
public class TransactionProcessor {
    private static final Logger LOG = Logger.getLogger(TransactionProcessor.class.getName());
    private final TransactionValidator validator;
    private final int maxAttempts;

    private final Map<String, TransactionRecord> transactionStore = new LinkedHashMap<>();
    private final Map<Long, TransactionRecord> waitingBySequence = new TreeMap<>();
    private final Map<String, BigDecimal> balances = new HashMap<>();
    private long nextExpectedSequence = 1;
    private long duplicateCount = 0;
    private long invalidCount = 0;

    public TransactionProcessor(TransactionValidator validator, int maxAttempts) {
        this.validator = validator;
        this.maxAttempts = maxAttempts;
    }

    public synchronized List<ProcessingResult> processBatch(List<TransactionRequest> requests) {
        List<ProcessingResult> results = new ArrayList<>();

        for (TransactionRequest request : requests) {
            Optional<String> validationError = validator.validate(request);
            if (validationError.isPresent()) {
            	invalidCount++;
                TransactionRecord invalid = new TransactionRecord(request);
                invalid.setStatus(TransactionStatus.FAILED);
                invalid.setMessage(validationError.get());
                results.add(ProcessingResult.from(invalid));
                LOG.warning("Rejected invalid transaction: " + safeId(request));
              
                
                if (request != null && request.sequenceNumber() == nextExpectedSequence) {
                    nextExpectedSequence++;
                    drainWaiting();
                }
                continue;
            }

            TransactionRecord existing = transactionStore.get(request.transactionId());
            if (existing != null) {
            	duplicateCount++;
                TransactionRecord duplicate = new TransactionRecord(request);
                duplicate.setStatus(TransactionStatus.DUPLICATE);
                duplicate.setMessage("Business transaction already registered as " + existing.getStatus());
                results.add(ProcessingResult.from(duplicate));
                LOG.info("Duplicate ignored: " + request.transactionId());
                continue;
            }

            TransactionRecord record = new TransactionRecord(request);
            transactionStore.put(request.transactionId(), record);

            if (request.sequenceNumber() < nextExpectedSequence) {
                record.setStatus(TransactionStatus.FAILED);
                record.setMessage("Stale sequence number; expected " + nextExpectedSequence);
            } else if (request.sequenceNumber() > nextExpectedSequence) {
                record.setStatus(TransactionStatus.RETRY_PENDING);
                record.setMessage("Waiting for sequence " + nextExpectedSequence);
                waitingBySequence.put(request.sequenceNumber(), record);
                LOG.info("Transaction queued for ordering: " + request.transactionId());
            } else {
                processInOrder(record);
                drainWaiting();
            }
            results.add(ProcessingResult.from(record));
        }

        retryEligibleRecords();
        // Return final status for every unique request in this batch. Duplicate/invalid
        
        List<ProcessingResult> finalResults = new ArrayList<>();
        for (ProcessingResult result : results) {
            TransactionRecord stored = transactionStore.get(result.transactionId());
            if (stored != null && !TransactionStatus.DUPLICATE.equals(result.status())) {
                finalResults.add(ProcessingResult.from(stored));
            } else {
                finalResults.add(result);
            }
        }
        return finalResults;
    }

    private void drainWaiting() {
        while (true) {
            TransactionRecord next = waitingBySequence.get(nextExpectedSequence);
            if (next == null) return;
            waitingBySequence.remove(nextExpectedSequence);
            processInOrder(next);
            if (next.getStatus() != TransactionStatus.PROCESSED) return;
        }
    }

    private void processInOrder(TransactionRecord record) {
        TransactionRequest request = record.getRequest();
        record.setStatus(TransactionStatus.PROCESSING);
        record.incrementAttempts();

        try {
            if (request.failFirstAttempt() && record.getAttempts() == 1) {
                throw new TransientProcessingException("Simulated temporary downstream error");
            }

            BigDecimal current = balances.getOrDefault(request.accountId(), BigDecimal.ZERO);
            BigDecimal updated = calculateBalance(current, request);
            // Commit balance and terminal status together in this synchronized critical section.
            balances.put(request.accountId(), updated);
            record.setStatus(TransactionStatus.PROCESSED);
            record.setMessage("Transaction applied successfully");
            nextExpectedSequence++;
            LOG.info("Processed transaction " + request.transactionId());
        } catch (TransientProcessingException exception) {
            record.setMessage(exception.getMessage());
            if (record.getAttempts() < maxAttempts) {
                record.setStatus(TransactionStatus.RETRY_PENDING);
                waitingBySequence.put(request.sequenceNumber(), record);
                LOG.warning("Retry pending for " + request.transactionId());
            } else {
                record.setStatus(TransactionStatus.FAILED);
                record.setMessage("Retry limit reached: " + exception.getMessage());
                nextExpectedSequence++;
                LOG.severe("Retry exhausted for " + request.transactionId());
            }
        } catch (RuntimeException exception) {
            record.setStatus(TransactionStatus.FAILED);
            record.setMessage("Processing failed safely");
            nextExpectedSequence++;
            LOG.severe("Processing failed for " + request.transactionId());
        }
    }

    private void retryEligibleRecords() {
        int safetyCounter = 0;
        while (safetyCounter++ < 100) {
            TransactionRecord next = waitingBySequence.get(nextExpectedSequence);
            if (next == null) return;
            waitingBySequence.remove(nextExpectedSequence);
            processInOrder(next);
            if (next.getStatus() != TransactionStatus.PROCESSED
                    && next.getStatus() != TransactionStatus.FAILED) return;
            drainWaiting();
        }
    }

    private BigDecimal calculateBalance(BigDecimal current, TransactionRequest request) {
        return switch (request.transactionType()) {
            case CREDIT, REVERSAL -> current.add(request.amount());
            case DEBIT -> {
                if (current.compareTo(request.amount()) < 0) {
                    throw new IllegalArgumentException("Insufficient funds");
                }
                yield current.subtract(request.amount());
            }
        };
    }

    public synchronized BigDecimal getBalance(String accountId) {
        return balances.getOrDefault(accountId, BigDecimal.ZERO);
    }

    public synchronized Map<TransactionStatus, Long> getStatusSummary() {
        Map<TransactionStatus, Long> summary =
                new EnumMap<>(TransactionStatus.class);

        for (TransactionStatus status : TransactionStatus.values()) {
            summary.put(status, 0L);
        }

        // Count unique transactions
        for (TransactionRecord record : transactionStore.values()) {
            summary.put(
                record.getStatus(),
                summary.get(record.getStatus()) + 1
            );
        }

        // Include duplicate and invalid attempts
        summary.put(TransactionStatus.DUPLICATE, duplicateCount);

        summary.put(
            TransactionStatus.FAILED,
            summary.get(TransactionStatus.FAILED) + invalidCount
        );

        return summary;
    }

    public synchronized Optional<TransactionRecord> findTransaction(String transactionId) {
        return Optional.ofNullable(transactionStore.get(transactionId));
    }

    private String safeId(TransactionRequest request) {
        return request == null || request.transactionId() == null ? "<unknown>" : request.transactionId();
    }

    private static class TransientProcessingException extends RuntimeException {
        private TransientProcessingException(String message) { super(message); }
    }
}
