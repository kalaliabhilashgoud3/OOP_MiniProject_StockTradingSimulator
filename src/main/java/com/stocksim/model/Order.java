package com.stocksim.model;

import java.time.Instant;
import java.util.UUID;

public abstract class Order {
    private final String id=UUID.randomUUID().toString().substring(0,8); private final Trader trader; private final Stock stock; private final double price; private int quantity; private final Instant time=Instant.now();
    protected Order(Trader trader,Stock stock,double price,int quantity){if(trader==null||stock==null||!Double.isFinite(price)||price<=0||quantity<=0)throw new IllegalArgumentException("Order needs a trader, stock, positive price and quantity.");this.trader=trader;this.stock=stock;this.price=price;this.quantity=quantity;}
    public abstract String getSide(); public String getId(){return id;} public Trader getTrader(){return trader;} public Stock getStock(){return stock;} public double getPrice(){return price;} public synchronized int getQuantity(){return quantity;} public Instant getTime(){return time;}
    public synchronized void fill(int amount){if(amount<=0||amount>quantity)throw new IllegalArgumentException("Invalid fill quantity.");quantity-=amount;}
}
