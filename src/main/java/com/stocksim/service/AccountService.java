package com.stocksim.service;

import com.stocksim.exception.InsufficientFundsException;
import com.stocksim.model.TransactionRecord;
import com.stocksim.model.TransactionType;
import com.stocksim.model.Trader;
import java.util.Objects;

/** Deposits and withdrawals operate on virtual simulator funds only. */
public class AccountService {
    private final TransactionLedger ledger;

    public AccountService(TransactionLedger ledger) { this.ledger = Objects.requireNonNull(ledger); }

    public void deposit(Trader trader, double amount) {
        validateAmount(amount);
        trader.credit(amount);
        ledger.add(new TransactionRecord(trader.getId(), TransactionType.DEPOSIT,
                amount, "Virtual funds deposit"));
    }

    public void withdraw(Trader trader, double amount) throws InsufficientFundsException {
        validateAmount(amount);
        trader.debit(amount);
        ledger.add(new TransactionRecord(trader.getId(), TransactionType.WITHDRAWAL,
                -amount, "Virtual funds withdrawal"));
    }

    private static void validateAmount(double amount) {
        if (!Double.isFinite(amount) || amount <= 0) {
            throw new IllegalArgumentException("Enter an amount greater than zero.");
        }
    }
}
