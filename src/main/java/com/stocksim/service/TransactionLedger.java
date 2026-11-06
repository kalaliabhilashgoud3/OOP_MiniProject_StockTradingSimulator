package com.stocksim.service;

import com.stocksim.model.TransactionRecord;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** In-memory activity ledger. PersistenceService writes it with the rest of the account data. */
public class TransactionLedger {
    private final List<TransactionRecord> records = new ArrayList<>();

    public synchronized void add(TransactionRecord record) { records.add(record); }
    public synchronized void addAll(Collection<TransactionRecord> values) { records.addAll(values); }
    public synchronized List<TransactionRecord> getAll() { return List.copyOf(records); }
}
