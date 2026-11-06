package com.stocksim.model;

public class Stock implements Tradable {
    private final String symbol;
    private final String name;
    private volatile double price;
    public Stock(String symbol, String name, double price) {
        if (symbol == null || symbol.isBlank() || name == null || name.isBlank() || !Double.isFinite(price) || price <= 0) throw new IllegalArgumentException("Stock needs a symbol, name and positive price.");
        this.symbol=symbol.toUpperCase(); this.name=name; this.price=price;
    }
    public String getSymbol(){return symbol;} public String getName(){return name;} public double getPrice(){return price;}
    public synchronized void setPrice(double price){if(!Double.isFinite(price)||price<=0)throw new IllegalArgumentException("Price must be positive.");this.price=price;}
    @Override public String toString(){return symbol+" - "+name;}
}
