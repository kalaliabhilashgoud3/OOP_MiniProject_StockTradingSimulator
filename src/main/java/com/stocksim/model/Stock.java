package com.stocksim.model;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** A simulated exchange listing. All values in this class are simulator data, not live quotes. */
public class Stock implements Tradable {
    private static final int MAX_HISTORY = 2_000;

    private final String symbol;
    private final String name;
    private final double marketCap;
    private final double volatility;
    private volatile double price;
    private volatile double previousClose;
    private volatile double open;
    private volatile double dayHigh;
    private volatile double dayLow;
    private volatile double weekHigh;
    private volatile double weekLow;
    private volatile long volume;
    private final Deque<PricePoint> history = new ArrayDeque<>();

    public Stock(String symbol, String name, double price) {
        this(symbol, name, price, price, price, price, price, price, price,
                1_000_000L, 100_000_000_000.0, 0.015);
    }

    public Stock(String symbol, String name, double price, double previousClose,
                 double open, double dayHigh, double dayLow, double weekHigh,
                 double weekLow, long volume, double marketCap, double volatility) {
        if (symbol == null || symbol.isBlank() || name == null || name.isBlank()) {
            throw new IllegalArgumentException("Stock symbol and company name are required.");
        }
        validatePrice(price);
        validatePrice(previousClose);
        validatePrice(open);
        this.symbol = symbol.trim().toUpperCase();
        this.name = name.trim();
        this.price = price;
        this.previousClose = previousClose;
        this.open = open;
        this.dayHigh = Math.max(dayHigh, price);
        this.dayLow = Math.min(dayLow, price);
        this.weekHigh = Math.max(weekHigh, price);
        this.weekLow = Math.min(weekLow, price);
        this.volume = Math.max(0, volume);
        this.marketCap = Math.max(0, marketCap);
        this.volatility = Math.max(0.001, Math.min(0.25, volatility));
        history.add(new PricePoint(Instant.now(), price, this.volume));
    }

    private static void validatePrice(double value) {
        if (!Double.isFinite(value) || value <= 0) {
            throw new IllegalArgumentException("Stock prices must be finite and positive.");
        }
    }

    @Override public String getSymbol() { return symbol; }
    public String getName() { return name; }
    @Override public double getPrice() { return price; }
    public double getPreviousClose() { return previousClose; }
    public double getOpen() { return open; }
    public double getDayHigh() { return dayHigh; }
    public double getDayLow() { return dayLow; }
    public double getWeekHigh() { return weekHigh; }
    public double getWeekLow() { return weekLow; }
    public long getVolume() { return volume; }
    public double getMarketCap() { return marketCap; }
    public double getVolatility() { return volatility; }
    public double getChange() { return price - previousClose; }
    public double getChangePercent() { return previousClose == 0 ? 0 : getChange() * 100.0 / previousClose; }

    public synchronized void setPrice(double nextPrice) {
        applyMarketTick(nextPrice, 0, Instant.now());
    }

    public synchronized void applyMarketTick(double nextPrice, long tradedVolume, Instant timestamp) {
        validatePrice(nextPrice);
        price = nextPrice;
        dayHigh = Math.max(dayHigh, nextPrice);
        dayLow = Math.min(dayLow, nextPrice);
        weekHigh = Math.max(weekHigh, nextPrice);
        weekLow = Math.min(weekLow, nextPrice);
        volume = Math.max(0, volume + tradedVolume);
        history.addLast(new PricePoint(timestamp, nextPrice, volume));
        while (history.size() > MAX_HISTORY) history.removeFirst();
    }

    public synchronized void resetSession() {
        previousClose = price;
        open = price;
        dayHigh = price;
        dayLow = price;
        volume = 0;
    }

    public synchronized List<PricePoint> getPriceHistory() {
        return new ArrayList<>(history);
    }

    public synchronized void restoreHistory(List<PricePoint> points) {
        history.clear();
        points.stream().skip(Math.max(0, points.size() - MAX_HISTORY)).forEach(history::addLast);
        if (history.isEmpty()) history.add(new PricePoint(Instant.now(), price, volume));
    }

    @Override public String toString() { return symbol + " - " + name; }
}
