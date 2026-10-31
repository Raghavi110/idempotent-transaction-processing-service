package com.example.transaction.source;

import com.example.transaction.model.TransactionRequest;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public class JsonFileTransactionSource implements TransactionSource {
    private final Path inputFile;
    private final ObjectMapper mapper = new ObjectMapper();

    public JsonFileTransactionSource(Path inputFile) {
        this.inputFile = inputFile;
    }

    @Override
    public List<TransactionRequest> load() throws IOException {
        return mapper.readValue(inputFile.toFile(), new TypeReference<>() {});
    }
}
