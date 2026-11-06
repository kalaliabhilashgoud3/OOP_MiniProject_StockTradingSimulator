package com.stocksim.model;

import java.util.HashMap;
import java.util.Map;

/** Tracks share quantity, weighted average cost, and realized profit for one trader. */
public class Portfolio {
    private final Map<String, Position> positions = new HashMap<>();
    private double realizedProfitLoss;

    public synchronized void add(String symbol, int quantity, double price) {
        validate(symbol, quantity, price);
        String key = symbol.toUpperCase();
        Position old = positions.get(key);
        int nextQuantity = (old == null ? 0 : old.quantity) + quantity;
        double oldCost = old == null ? 0 : old.averagePrice * old.quantity;
        positions.put(key, new Position(nextQuantity, (oldCost + price * quantity) / nextQuantity));
    }

    public synchronized double remove(String symbol, int quantity, double salePrice) {
        validate(symbol, quantity, salePrice);
        String key = symbol.toUpperCase();
        Position position = positions.get(key);
        if (position == null || position.quantity < quantity) {
            throw new IllegalArgumentException("Not enough shares available to sell.");
        }
        double realized = (salePrice - position.averagePrice) * quantity;
        realizedProfitLoss += realized;
        if (position.quantity == quantity) {
            positions.remove(key);
        } else {
            positions.put(key, new Position(position.quantity - quantity, position.averagePrice));
        }
        return realized;
    }

    /** Compatibility overload for earlier code that removed shares without a sale price. */
    public synchronized void remove(String symbol, int quantity) {
        Position position = positions.get(symbol.toUpperCase());
        if (position == null) throw new IllegalArgumentException("No shares are held for " + symbol + ".");
        remove(symbol, quantity, position.averagePrice);
    }

    private static void validate(String symbol, int quantity, double price) {
        if (symbol == null || symbol.isBlank() || quantity <= 0 || !Double.isFinite(price) || price <= 0) {
            throw new IllegalArgumentException("Symbol, positive quantity and positive price are required.");
        }
    }

    public synchronized int getQuantity(String symbol) {
        Position position = positions.get(symbol.toUpperCase());
        return position == null ? 0 : position.quantity;
    }

    public synchronized double getAveragePrice(String symbol) {
        Position position = positions.get(symbol.toUpperCase());
        return position == null ? 0 : position.averagePrice;
    }

    public synchronized Map<String, Integer> getHoldings() {
        Map<String, Integer> copy = new HashMap<>();
        positions.forEach((symbol, position) -> copy.put(symbol, position.quantity));
        return Map.copyOf(copy);
    }

    public synchronized Map<String, Holding> getPositions() {
        Map<String, Holding> copy = new HashMap<>();
        positions.forEach((symbol, position) -> copy.put(symbol,
                new Holding(symbol, position.quantity, position.averagePrice)));
        return Map.copyOf(copy);
    }

    public synchronized double value(Map<String, Stock> stocks) {
        return positions.entrySet().stream()
                .mapToDouble(entry -> {
                    Stock stock = stocks.get(entry.getKey());
                    return stock == null ? 0 : entry.getValue().quantity * stock.getPrice();
                }).sum();
    }

    public synchronized double cost() {
        return positions.values().stream().mapToDouble(p -> p.quantity * p.averagePrice).sum();
    }

    public synchronized double getRealizedProfitLoss() { return realizedProfitLoss; }

    public synchronized void restorePosition(String symbol, int quantity, double averagePrice) {
        validate(symbol, quantity, averagePrice);
        positions.put(symbol.toUpperCase(), new Position(quantity, averagePrice));
    }

    public synchronized void restoreRealizedProfitLoss(double amount) {
        if (!Double.isFinite(amount)) throw new IllegalArgumentException("Realized P/L must be finite.");
        realizedProfitLoss = amount;
    }

    public record Holding(String symbol, int quantity, double averagePrice) { }
    private record Position(int quantity, double averagePrice) { }
}
