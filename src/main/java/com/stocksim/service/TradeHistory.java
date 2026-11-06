package com.stocksim.service;
import com.stocksim.model.Trade;
import java.util.*;
public class TradeHistory {
    private final List<Trade> trades=new ArrayList<>();
    public synchronized void add(Trade trade){trades.add(trade);}
    public synchronized void addAll(Collection<Trade> records){trades.addAll(records);}
    public synchronized List<Trade> getAll(){return List.copyOf(trades);}
}
