package com.stocksim.model;

import java.time.Instant;

public class SellOrder extends Order {
    public SellOrder(Trader trader, Stock stock, double price, int quantity) {
        super(trader, stock, price, quantity);
    }

    public SellOrder(Trader trader, Stock stock, OrderType type, int quantity,
                     double price, double triggerPrice) {
        super(trader, stock, type, quantity, price, triggerPrice);
    }

    public SellOrder(String id, Trader trader, Stock stock, OrderType type, double price,
                     double triggerPrice, int originalQuantity, Instant time,
                     OrderStatus status, int remainingQuantity) {
        super(id, trader, stock, type, price, triggerPrice, originalQuantity,
                time, status, remainingQuantity);
    }

    @Override public String getSide() { return "SELL"; }
}
