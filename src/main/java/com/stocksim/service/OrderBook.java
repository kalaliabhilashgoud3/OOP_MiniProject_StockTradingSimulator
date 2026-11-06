package com.stocksim.service;

import com.stocksim.exception.InsufficientFundsException;
import com.stocksim.model.BuyOrder;
import com.stocksim.model.Order;
import com.stocksim.model.OrderStatus;
import com.stocksim.model.OrderType;
import com.stocksim.model.SellOrder;
import com.stocksim.model.Stock;
import com.stocksim.model.Trade;
import com.stocksim.model.Trader;
import com.stocksim.model.TransactionRecord;
import com.stocksim.model.TransactionType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;

/** Price-time priority matcher with stop orders, cancellation, settlement, and virtual charges. */
public class OrderBook {
    public static final double CHARGE_RATE = 0.0005; // Simulated brokerage and exchange charges: 0.05%.
    private static final Comparator<BuyOrder> BUY_PRIORITY = Comparator
            .comparingDouble(BuyOrder::getPrice).reversed().thenComparing(Order::getTime);
    private static final Comparator<SellOrder> SELL_PRIORITY = Comparator
            .comparingDouble(SellOrder::getPrice).thenComparing(Order::getTime);

    private final Map<String, PriorityQueue<BuyOrder>> buys = new HashMap<>();
    private final Map<String, PriorityQueue<SellOrder>> sells = new HashMap<>();
    private final Map<String, Order> orders = new LinkedHashMap<>();
    private final Map<String, Double> reservedBuyPrice = new HashMap<>();
    private final TradeHistory history;
    private final TransactionLedger ledger;

    public OrderBook(TradeHistory history) { this(history, new TransactionLedger()); }

    public OrderBook(TradeHistory history, TransactionLedger ledger) {
        this.history = Objects.requireNonNull(history);
        this.ledger = Objects.requireNonNull(ledger);
    }

    public synchronized Order submit(Order order) throws InsufficientFundsException {
        Objects.requireNonNull(order, "Order is required.");
        if (orders.containsKey(order.getId())) throw new IllegalArgumentException("Order ID already exists.");
        orders.put(order.getId(), order);
        boolean reservationCreated = false;
        try {
            if (isBuy(order)) {
                reserveCash(order);
                reservationCreated = true;
            } else {
                reserveShares(order);
                reservationCreated = true;
            }

            if (order.getType() == OrderType.MARKET) {
                executeAgainstSimulatedMarket(order, order.getStock().getPrice());
            } else if (order.getType() == OrderType.STOP_LOSS || order.getType() == OrderType.STOP_LIMIT) {
                order.setStatus(OrderStatus.TRIGGER_PENDING);
            } else {
                addToBook(order);
                match(order.getStock());
            }
            return order;
        } catch (InsufficientFundsException | IllegalArgumentException | IllegalStateException ex) {
            if (reservationCreated) releaseReservation(order);
            else orders.remove(order.getId());
            order.setStatus(OrderStatus.REJECTED);
            throw ex;
        }
    }

    /** Compatibility method retained for the original project API. */
    public synchronized void placeBuy(BuyOrder order) throws InsufficientFundsException { submit(order); }

    /** Compatibility method retained for the original project API. */
    public synchronized void placeSell(SellOrder order) {
        try {
            submit(order);
        } catch (InsufficientFundsException impossibleForSell) {
            throw new IllegalStateException("A sell order cannot require wallet funds.", impossibleForSell);
        }
    }

    private void reserveCash(Order order) throws InsufficientFundsException {
        double reference = order.getType() == OrderType.MARKET ? order.getStock().getPrice() : order.getPrice();
        if (order.getType() == OrderType.STOP_LOSS) reference = order.getTriggerPrice() * 1.05;
        double reservePrice = reference;
        double amount = reservePrice * order.getRemainingQuantity() * (1 + CHARGE_RATE);
        order.getTrader().debit(amount);
        reservedBuyPrice.put(order.getId(), reservePrice);
    }

    private void reserveShares(Order order) {
        int alreadyReserved = orders.values().stream()
                .filter(existing -> existing != order && !isBuy(existing)
                        && existing.getTrader().equals(order.getTrader())
                        && existing.getStock().getSymbol().equals(order.getStock().getSymbol())
                        && isActive(existing))
                .mapToInt(Order::getRemainingQuantity).sum();
        int available = order.getTrader().getPortfolio().getQuantity(order.getStock().getSymbol()) - alreadyReserved;
        if (available < order.getRemainingQuantity()) {
            throw new IllegalArgumentException("Not enough available shares. Pending sell orders already reserve some holdings.");
        }
    }

    private void addToBook(Order order) {
        if (isBuy(order)) buyQueue(order.getStock().getSymbol()).add((BuyOrder) order);
        else sellQueue(order.getStock().getSymbol()).add((SellOrder) order);
        order.setStatus(OrderStatus.PENDING);
    }

    private void match(Stock stock) {
        PriorityQueue<BuyOrder> buyBook = buys.get(stock.getSymbol());
        PriorityQueue<SellOrder> sellBook = sells.get(stock.getSymbol());
        if (buyBook == null || sellBook == null) return;

        while (!buyBook.isEmpty() && !sellBook.isEmpty()) {
            BuyOrder buy = buyBook.peek();
            SellOrder sell = sellBook.peek();
            if (buy.getPrice() < sell.getPrice()) return;

            if (buy.getTrader().equals(sell.getTrader())) {
                cancelInternal(buy.getTime().isAfter(sell.getTime()) ? buy : sell,
                        "Self-trade prevention");
                continue;
            }

            int quantity = Math.min(buy.getRemainingQuantity(), sell.getRemainingQuantity());
            double executionPrice = sell.getTime().isBefore(buy.getTime()) ? sell.getPrice() : buy.getPrice();
            settle(buy, sell, quantity, executionPrice);
            buy.fill(quantity);
            sell.fill(quantity);
            history.add(new Trade(stock.getSymbol(), buy.getTrader().getId(),
                    sell.getTrader().getId(), quantity, executionPrice));
            stock.applyMarketTick(executionPrice, quantity, Instant.now());
            if (buy.getStatus() == OrderStatus.FILLED) {
                buyBook.poll();
                reservedBuyPrice.remove(buy.getId());
            }
            if (sell.getStatus() == OrderStatus.FILLED) sellBook.poll();
        }
    }

    private void settle(BuyOrder buy, SellOrder sell, int quantity, double price) {
        double value = money(price * quantity);
        double fee = money(value * CHARGE_RATE);
        double reserve = reservedBuyPrice.getOrDefault(buy.getId(), buy.getPrice());
        double buyerReserveSlice = money(reserve * quantity * (1 + CHARGE_RATE));
        double buyerActual = value + fee;
        double buyerAdjustment = buyerReserveSlice - buyerActual;
        if (buyerAdjustment >= 0) buy.getTrader().credit(buyerAdjustment);
        else {
            try {
                buy.getTrader().debit(-buyerAdjustment);
            } catch (InsufficientFundsException impossibleAfterReservation) {
                throw new IllegalStateException("Reserved buy funds did not cover settlement.", impossibleAfterReservation);
            }
        }
        double realized = sell.getTrader().getPortfolio().remove(sell.getStock().getSymbol(), quantity, price);
        buy.getTrader().getPortfolio().add(buy.getStock().getSymbol(), quantity, price);
        sell.getTrader().credit(value - fee);
        ledger.add(new TransactionRecord(buy.getTrader().getId(), TransactionType.BUY_SETTLEMENT,
                -value, "Buy " + quantity + " " + buy.getStock().getSymbol()));
        ledger.add(new TransactionRecord(sell.getTrader().getId(), TransactionType.SELL_SETTLEMENT,
                value, "Sell " + quantity + " " + sell.getStock().getSymbol()));
        ledger.add(new TransactionRecord(sell.getTrader().getId(), TransactionType.REALIZED_PNL,
                realized, "Trade " + sell.getStock().getSymbol()));
        if (fee > 0) {
            ledger.add(new TransactionRecord(buy.getTrader().getId(), TransactionType.BROKERAGE,
                    -fee, "Buy-side simulated charges"));
            ledger.add(new TransactionRecord(sell.getTrader().getId(), TransactionType.BROKERAGE,
                    -fee, "Sell-side simulated charges"));
        }
    }

    /** Stop orders execute only after the simulator price crosses their trigger. */
    public synchronized void onPriceTick(Stock stock) {
        for (Order order : new ArrayList<>(orders.values())) {
            if (!order.getStock().equals(stock) || order.getStatus() != OrderStatus.TRIGGER_PENDING) continue;
            boolean isTriggered = isBuy(order)
                    ? stock.getPrice() >= order.getTriggerPrice()
                    : stock.getPrice() <= order.getTriggerPrice();
            if (!isTriggered) continue;
            order.markTriggered();
            if (order.getType() == OrderType.STOP_LOSS) {
                executeAgainstSimulatedMarket(order, stock.getPrice());
            } else {
                addToBook(order);
                match(stock);
            }
        }
    }

    private void executeAgainstSimulatedMarket(Order order, double price) {
        int quantity = order.getRemainingQuantity();
        double value = money(price * quantity);
        double fee = money(value * CHARGE_RATE);
        if (isBuy(order)) {
            double reserve = reservedBuyPrice.getOrDefault(order.getId(), price);
            double held = money(reserve * quantity * (1 + CHARGE_RATE));
            double actual = value + fee;
            if (actual > held) {
                try {
                    order.getTrader().debit(actual - held);
                } catch (InsufficientFundsException ex) {
                    order.setStatus(OrderStatus.REJECTED);
                    throw new IllegalArgumentException("Available virtual balance no longer covers this triggered order.", ex);
                }
            } else {
                order.getTrader().credit(held - actual);
            }
            order.getTrader().getPortfolio().add(order.getStock().getSymbol(), quantity, price);
            ledger.add(new TransactionRecord(order.getTrader().getId(), TransactionType.BUY_SETTLEMENT,
                    -value, "Simulated market buy " + order.getStock().getSymbol()));
        } else {
            double realized = order.getTrader().getPortfolio().remove(order.getStock().getSymbol(), quantity, price);
            order.getTrader().credit(value - fee);
            ledger.add(new TransactionRecord(order.getTrader().getId(), TransactionType.SELL_SETTLEMENT,
                    value, "Simulated market sell " + order.getStock().getSymbol()));
            ledger.add(new TransactionRecord(order.getTrader().getId(), TransactionType.REALIZED_PNL,
                    realized, "Trade " + order.getStock().getSymbol()));
        }
        if (fee > 0) ledger.add(new TransactionRecord(order.getTrader().getId(), TransactionType.BROKERAGE,
                -fee, "Simulated market order charges"));
        order.fill(quantity);
        reservedBuyPrice.remove(order.getId());
        history.add(new Trade(order.getStock().getSymbol(),
                isBuy(order) ? order.getTrader().getId() : "SIMULATED-LIQUIDITY",
                isBuy(order) ? "SIMULATED-LIQUIDITY" : order.getTrader().getId(), quantity, price));
        order.getStock().applyMarketTick(price, quantity, Instant.now());
    }

    public synchronized void cancel(String orderId, Trader requestingTrader) {
        Order order = orders.get(orderId);
        if (order == null) throw new IllegalArgumentException("Order was not found.");
        if (!order.getTrader().equals(requestingTrader)) throw new IllegalArgumentException("You can cancel only your own orders.");
        cancelInternal(order, "Cancelled by trader");
    }

    private void cancelInternal(Order order, String reason) {
        if (!isActive(order)) throw new IllegalStateException("Only active orders can be cancelled.");
        if (isBuy(order)) {
            double reserve = reservedBuyPrice.getOrDefault(order.getId(), order.getPrice());
            double refund = money(reserve * order.getRemainingQuantity() * (1 + CHARGE_RATE));
            if (refund > 0) {
                order.getTrader().credit(refund);
                ledger.add(new TransactionRecord(order.getTrader().getId(), TransactionType.ORDER_REFUND,
                        refund, reason));
            }
        }
        removeFromQueue(order);
        order.cancel();
    }

    private void releaseReservation(Order order) {
        if (isBuy(order)) {
            Double heldPrice = reservedBuyPrice.remove(order.getId());
            double reservePrice = heldPrice == null ? order.getPrice() : heldPrice;
            double amount = money(reservePrice * order.getRemainingQuantity() * (1 + CHARGE_RATE));
            if (amount > 0) order.getTrader().credit(amount);
        }
        removeFromQueue(order);
    }

    private void removeFromQueue(Order order) {
        if (isBuy(order)) {
            PriorityQueue<BuyOrder> queue = buys.get(order.getStock().getSymbol());
            if (queue != null) queue.remove(order);
        } else {
            PriorityQueue<SellOrder> queue = sells.get(order.getStock().getSymbol());
            if (queue != null) queue.remove(order);
        }
    }

    private static boolean isBuy(Order order) { return "BUY".equals(order.getSide()); }
    private static boolean isActive(Order order) {
        return order.getStatus() == OrderStatus.PENDING || order.getStatus() == OrderStatus.PARTIALLY_FILLED
                || order.getStatus() == OrderStatus.TRIGGER_PENDING;
    }
    private PriorityQueue<BuyOrder> buyQueue(String symbol) {
        return buys.computeIfAbsent(symbol, ignored -> new PriorityQueue<>(BUY_PRIORITY));
    }
    private PriorityQueue<SellOrder> sellQueue(String symbol) {
        return sells.computeIfAbsent(symbol, ignored -> new PriorityQueue<>(SELL_PRIORITY));
    }
    private static double money(double amount) { return Math.round(amount * 100.0) / 100.0; }

    public synchronized List<BuyOrder> getBuys() {
        return buys.values().stream().flatMap(Collection::stream).sorted(BUY_PRIORITY).toList();
    }
    public synchronized List<SellOrder> getSells() {
        return sells.values().stream().flatMap(Collection::stream).sorted(SELL_PRIORITY).toList();
    }
    public synchronized List<Order> getOrders() { return List.copyOf(orders.values()); }
    public synchronized List<Order> getActiveOrders() { return orders.values().stream().filter(OrderBook::isActive).toList(); }
    public synchronized Order getOrder(String id) { return orders.get(id); }
    public synchronized double getBestBid(String symbol) {
        PriorityQueue<BuyOrder> queue = buys.get(symbol.toUpperCase());
        return queue == null || queue.isEmpty() ? 0 : queue.peek().getPrice();
    }
    public synchronized double getBestAsk(String symbol) {
        PriorityQueue<SellOrder> queue = sells.get(symbol.toUpperCase());
        return queue == null || queue.isEmpty() ? 0 : queue.peek().getPrice();
    }
    public synchronized double getSpread(String symbol) {
        double bid = getBestBid(symbol), ask = getBestAsk(symbol);
        return bid == 0 || ask == 0 ? 0 : ask - bid;
    }
    public TransactionLedger getLedger() { return ledger; }

    /** Restores persisted orders without charging balances a second time. */
    public synchronized void restore(Order order) {
        orders.put(order.getId(), order);
        if (!isActive(order)) return;
        if (isBuy(order)) reservedBuyPrice.put(order.getId(), order.getPrice());
        if (order.getStatus() == OrderStatus.TRIGGER_PENDING) return;
        if (isBuy(order)) buyQueue(order.getStock().getSymbol()).add((BuyOrder) order);
        else sellQueue(order.getStock().getSymbol()).add((SellOrder) order);
    }
}
