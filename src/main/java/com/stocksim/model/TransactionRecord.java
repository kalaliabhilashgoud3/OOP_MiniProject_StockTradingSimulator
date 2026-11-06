package com.stocksim.model;

import java.time.Instant;
import java.util.UUID;

public record TransactionRecord(String id, String traderId, TransactionType type,
                                double amount, String description, Instant time) {
    public TransactionRecord(String traderId, TransactionType type, double amount, String description) {
        this(UUID.randomUUID().toString().substring(0, 8), traderId, type, amount,
                description, Instant.now());
    }
}
