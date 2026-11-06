package com.stocksim.model;

import java.time.Instant;
import java.util.UUID;

/** Common order details shared by buy and sell orders. */
public abstract class Order {
    private final String id;
    private final Trader trader;
    private final Stock stock;
    private final OrderType type;
    private final int originalQuantity;
    private final Instant time;
    private final double triggerPrice;
    private volatile double price;
    private volatile int remainingQuantity;
    private volatile OrderStatus status;
    private volatile boolean triggered;

    protected Order(Trader trader, Stock stock, double price, int quantity) {
        this(UUID.randomUUID().toString().substring(0, 8), trader, stock,
                OrderType.LIMIT, price, 0, quantity, Instant.now(), OrderStatus.PENDING, quantity);
    }

    protected Order(Trader trader, Stock stock, OrderType type, int quantity,
                    double price, double triggerPrice) {
        this(UUID.randomUUID().toString().substring(0, 8), trader, stock, type,
                price, triggerPrice, quantity, Instant.now(),
                type == OrderType.STOP_LIMIT || type == OrderType.STOP_LOSS
                        ? OrderStatus.TRIGGER_PENDING : OrderStatus.PENDING,
                quantity);
    }

    public Order(String id, Trader trader, Stock stock, OrderType type, double price,
                 double triggerPrice, int originalQuantity, Instant time,
                 OrderStatus status, int remainingQuantity) {
        if (id == null || id.isBlank() || trader == null || stock == null || type == null
                || originalQuantity <= 0 || remainingQuantity < 0
                || remainingQuantity > originalQuantity || time == null || status == null) {
            throw new IllegalArgumentException("Order details are incomplete or invalid.");
        }
        if (type != OrderType.MARKET && (!Double.isFinite(price) || price <= 0)) {
            throw new IllegalArgumentException("Order price must be positive.");
        }
        if ((type == OrderType.STOP_LIMIT || type == OrderType.STOP_LOSS)
                && (!Double.isFinite(triggerPrice) || triggerPrice <= 0)) {
            throw new IllegalArgumentException("Stop order trigger price must be positive.");
        }
        this.id = id;
        this.trader = trader;
        this.stock = stock;
        this.type = type;
        this.price = price;
        this.triggerPrice = triggerPrice;
        this.originalQuantity = originalQuantity;
        this.remainingQuantity = remainingQuantity;
        this.time = time;
        this.status = status;
        this.triggered = status != OrderStatus.TRIGGER_PENDING;
    }

    public abstract String getSide();
    public String getId() { return id; }
    public Trader getTrader() { return trader; }
    public Stock getStock() { return stock; }
    public double getPrice() { return price; }
    public double getTriggerPrice() { return triggerPrice; }
    public OrderType getType() { return type; }
    public int getQuantity() { return remainingQuantity; }
    public int getRemainingQuantity() { return remainingQuantity; }
    public int getOriginalQuantity() { return originalQuantity; }
    public Instant getTime() { return time; }
    public OrderStatus getStatus() { return status; }
    public boolean isTriggered() { return triggered; }

    public synchronized void fill(int amount) {
        if (amount <= 0 || amount > remainingQuantity) {
            throw new IllegalArgumentException("Invalid order fill quantity.");
        }
        remainingQuantity -= amount;
        status = remainingQuantity == 0 ? OrderStatus.FILLED : OrderStatus.PARTIALLY_FILLED;
    }

    public synchronized void cancel() {
        if (status == OrderStatus.FILLED || status == OrderStatus.CANCELLED) {
            throw new IllegalStateException("Only active orders can be cancelled.");
        }
        status = OrderStatus.CANCELLED;
    }

    public synchronized void markTriggered() {
        if (status == OrderStatus.TRIGGER_PENDING) {
            triggered = true;
            status = OrderStatus.PENDING;
        }
    }

    public synchronized void setStatus(OrderStatus nextStatus) { status = nextStatus; }
}
