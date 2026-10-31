package com.example.transaction.model;

public class TransactionRecord {
    private final TransactionRequest request;
    private TransactionStatus status;
    private int attempts;
    private String message;

    public TransactionRecord(TransactionRequest request) {
        this.request = request;
        this.status = TransactionStatus.RECEIVED;
        this.message = "Accepted";
    }

    public TransactionRequest getRequest() { return request; }
    public TransactionStatus getStatus() { return status; }
    public void setStatus(TransactionStatus status) { this.status = status; }
    public int getAttempts() { return attempts; }
    public void incrementAttempts() { attempts++; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
