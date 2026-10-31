package com.example.transaction.source;

import com.example.transaction.model.TransactionRequest;
import java.io.IOException;
import java.util.List;

public interface TransactionSource {
    List<TransactionRequest> load() throws IOException;
}
