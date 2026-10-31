package com.example.transaction;

import com.example.transaction.model.*;
import com.example.transaction.service.TransactionProcessor;
import com.example.transaction.service.TransactionValidator;
import com.example.transaction.source.JsonFileTransactionSource;
import com.example.transaction.source.TransactionSource;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class Application {
    public static void main(String[] args) throws Exception {
        Path input = args.length > 0 ? Path.of(args[0]) : Path.of("sample-data/transactions.json");
        TransactionSource source = new JsonFileTransactionSource(input);
        List<TransactionRequest> requests = source.load();

        TransactionProcessor processor = new TransactionProcessor(new TransactionValidator(), 3);
        List<ProcessingResult> results = processor.processBatch(requests);

        System.out.println("\n*** TRANSACTION RESULTS ***");
        results.forEach(result -> System.out.printf(
                "%s | %s | %s | attempts=%d | %s%n",
                result.transactionId(), result.requestId(), result.status(),
                result.attempts(), result.message()));

        System.out.println("\n*** ACCOUNT BALANCES ***");
        for (String account : List.of("ACC-201", "ACC-202", "ACC-203")) {
            System.out.println(account + " = " + processor.getBalance(account));
        }

        System.out.println("\n*** STATUS SUMMARY ***");
        Map<TransactionStatus, Long> summary = processor.getStatusSummary();
        summary.forEach((status, count) -> System.out.println(status + " = " + count));
    }
}
