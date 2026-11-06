package com.stocksim.service;

import com.stocksim.model.PricePoint;
import com.stocksim.model.Trader;
import com.stocksim.model.Stock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Keeps time-stamped virtual account value snapshots for portfolio charts. */
public class PerformanceHistory {
    private static final int LIMIT = 3_000;
    private final Map<String, Deque<AccountPoint>> history = new LinkedHashMap<>();

    public synchronized void record(Iterable<Trader> traders, Map<String, Stock> stocks) {
        Instant now = Instant.now();
        for (Trader trader : traders) {
            double total = trader.getWallet() + trader.getPortfolio().value(stocks);
            Deque<AccountPoint> points = history.computeIfAbsent(trader.getId(), ignored -> new ArrayDeque<>());
            points.addLast(new AccountPoint(now, total));
            while (points.size() > LIMIT) points.removeFirst();
        }
    }

    public synchronized List<AccountPoint> get(String traderId) {
        return List.copyOf(history.getOrDefault(traderId, new ArrayDeque<>()));
    }

    public synchronized Map<String, List<AccountPoint>> snapshot() {
        Map<String, List<AccountPoint>> copy = new LinkedHashMap<>();
        history.forEach((trader, points) -> copy.put(trader, List.copyOf(points)));
        return Map.copyOf(copy);
    }

    public synchronized void restore(String traderId, List<AccountPoint> points) {
        Deque<AccountPoint> restored = new ArrayDeque<>();
        points.stream().skip(Math.max(0, points.size() - LIMIT)).forEach(restored::addLast);
        history.put(traderId, restored);
    }

    public record AccountPoint(Instant time, double value) { }
}
