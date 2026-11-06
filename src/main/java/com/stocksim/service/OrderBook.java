package com.stocksim.service;

import com.stocksim.exception.InsufficientFundsException;
import com.stocksim.model.*;
import java.util.*;

/** Price-time priority book. Better prices win; equal prices use the earlier timestamp. */
public class OrderBook {
    // Each symbol has its own two queues so orders for different stocks never block matching.
    private final Map<String, PriorityQueue<BuyOrder>> buys = new HashMap<>();
    private final Map<String, PriorityQueue<SellOrder>> sells = new HashMap<>();
    private static final Comparator<BuyOrder> BUY_PRIORITY = Comparator
            .comparingDouble(BuyOrder::getPrice).reversed().thenComparing(Order::getTime);
    private static final Comparator<SellOrder> SELL_PRIORITY = Comparator
            .comparingDouble(SellOrder::getPrice).thenComparing(Order::getTime);
    private final TradeHistory history;

    public OrderBook(TradeHistory history) {
        this.history = Objects.requireNonNull(history);
    }

    /** Reserve the order's limit value before adding it, so a pending order cannot overspend. */
    public synchronized void placeBuy(BuyOrder order) throws InsufficientFundsException {
        Objects.requireNonNull(order, "Buy order is required.");
        double reservedAmount = order.getPrice() * order.getQuantity();
        order.getTrader().debit(reservedAmount);

        try {
            buyQueue(order.getStock().getSymbol()).add(order);
        } catch (RuntimeException ex) {
            // If adding the order fails, release its reserved wallet amount.
            order.getTrader().credit(reservedAmount);
            throw ex;
        }
        match(order.getStock());
    }

    /** Keep shares from being promised to more than one pending sell order. */
    public synchronized void placeSell(SellOrder order) {
        Objects.requireNonNull(order, "Sell order is required.");
        String symbol = order.getStock().getSymbol();
        int alreadyReserved = sellQueue(symbol).stream()
                .filter(existing -> existing.getTrader().equals(order.getTrader()))
                .mapToInt(Order::getQuantity)
                .sum();
        int available = order.getTrader().getPortfolio().getQuantity(symbol) - alreadyReserved;
        if (available < order.getQuantity()) {
            throw new IllegalArgumentException("Not enough unreserved shares to sell.");
        }

        sellQueue(symbol).add(order);
        match(order.getStock());
    }

    private PriorityQueue<BuyOrder> buyQueue(String symbol) {
        return buys.computeIfAbsent(symbol, key -> new PriorityQueue<>(BUY_PRIORITY));
    }

    private PriorityQueue<SellOrder> sellQueue(String symbol) {
        return sells.computeIfAbsent(symbol, key -> new PriorityQueue<>(SELL_PRIORITY));
    }

    /** Matches the best bid against the best ask while both prices cross. */
    private void match(Stock stock) {
        PriorityQueue<BuyOrder> buyBook = buys.get(stock.getSymbol());
        PriorityQueue<SellOrder> sellBook = sells.get(stock.getSymbol());
        if (buyBook == null || sellBook == null) return;

        while (!buyBook.isEmpty() && !sellBook.isEmpty()) {
            BuyOrder buy = buyBook.peek();
            SellOrder sell = sellBook.peek();
            if (buy.getPrice() < sell.getPrice()) return;

            int quantity = Math.min(buy.getQuantity(), sell.getQuantity());
            // The older resting order sets the trade price, which avoids favoring the latest arrival.
            double price = sell.getTime().isBefore(buy.getTime()) ? sell.getPrice() : buy.getPrice();
            // The buyer reserved their limit price. Return the unused part and pay the seller.
            buy.getTrader().credit((buy.getPrice() - price) * quantity);
            sell.getTrader().credit(price * quantity);
            buy.getTrader().getPortfolio().add(stock.getSymbol(), quantity, price);
            sell.getTrader().getPortfolio().remove(stock.getSymbol(), quantity);

            buy.fill(quantity);
            sell.fill(quantity);
            history.add(new Trade(stock.getSymbol(), buy.getTrader().getId(),
                    sell.getTrader().getId(), quantity, price));
            stock.setPrice(price);

            if (buy.getQuantity() == 0) buyBook.poll();
            if (sell.getQuantity() == 0) sellBook.poll();
        }
    }

    public synchronized List<BuyOrder> getBuys() {
        return buys.values().stream().flatMap(Collection::stream).sorted(BUY_PRIORITY).toList();
    }

    public synchronized List<SellOrder> getSells() {
        return sells.values().stream().flatMap(Collection::stream).sorted(SELL_PRIORITY).toList();
    }
}
