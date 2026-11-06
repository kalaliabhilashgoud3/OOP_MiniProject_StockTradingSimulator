package com.stocksim.service;

import com.stocksim.model.MarketIndex;
import com.stocksim.model.MarketSentiment;
import com.stocksim.model.PricePoint;
import com.stocksim.model.Stock;
import javax.swing.SwingUtilities;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

/** Offline random-walk market data, explicitly labeled as simulation data. */
public class MarketEngine implements AutoCloseable {
    private final Collection<Stock> stocks;
    private final CopyOnWriteArrayList<Runnable> listeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<String> events = new CopyOnWriteArrayList<>();
    private final AtomicBoolean running = new AtomicBoolean();
    private volatile boolean marketOpen = true;
    private volatile int tickMillis = 2_000;
    private volatile MarketSentiment sentiment = MarketSentiment.SIDEWAYS;
    private volatile long ticks;
    private Thread worker;

    public MarketEngine(Collection<Stock> stocks) { this.stocks = stocks; }

    public synchronized void start() {
        if (!running.compareAndSet(false, true)) return;
        worker = new Thread(this::runLoop, "simulated-market-engine");
        worker.setDaemon(true);
        worker.start();
    }

    private void runLoop() {
        while (running.get()) {
            if (marketOpen) tickOnce();
            try {
                Thread.sleep(tickMillis);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    public void tickOnce() {
        if (!marketOpen) return;
        ticks++;
        if (ticks % 90 == 1) chooseSentiment();
        synchronized (stocks) {
            for (Stock stock : stocks) {
                double marketNoise = ThreadLocalRandom.current().nextGaussian() * stock.getVolatility() * 0.008;
                double nextPrice = stock.getPrice() * Math.exp(sentiment.getDrift() + marketNoise);
                long volume = Math.max(1, (long) (Math.abs(marketNoise) * 500_000 + ThreadLocalRandom.current().nextLong(20, 800)));
                stock.applyMarketTick(Math.max(0.05, nextPrice), volume, Instant.now());
            }
        }
        if (ticks % 30 == 0) addEvent("Simulated market update · " + sentiment.getLabel());
        listeners.forEach(listener -> SwingUtilities.invokeLater(listener));
    }

    private void chooseSentiment() {
        MarketSentiment[] values = MarketSentiment.values();
        sentiment = values[ThreadLocalRandom.current().nextInt(values.length)];
        addEvent("Market mood changed to " + sentiment.getLabel() + " (simulated)");
    }

    public List<MarketIndex> getIndices() {
        return List.of(index("NIFTY 50", 22_500, List.of("RELIANCE", "TCS", "INFY", "ICICIBANK", "HDFCBANK", "ITC", "LT")),
                index("SENSEX", 74_000, List.of("RELIANCE", "TCS", "INFY", "ICICIBANK", "HDFCBANK", "ITC")),
                index("BANK NIFTY", 48_000, List.of("HDFCBANK", "ICICIBANK", "SBIN", "AXISBANK")));
    }

    private MarketIndex index(String name, double base, List<String> symbols) {
        Map<String, Stock> bySymbol = stocks.stream().collect(java.util.stream.Collectors.toMap(Stock::getSymbol, s -> s));
        double change = symbols.stream().map(bySymbol::get).filter(java.util.Objects::nonNull)
                .mapToDouble(Stock::getChangePercent).average().orElse(0);
        return new MarketIndex(name, base * (1 + change / 100), change, "SIMULATED INDEX");
    }

    public List<Stock> getTopGainers(int limit) {
        return stocks.stream().sorted(Comparator.comparingDouble(Stock::getChangePercent).reversed())
                .limit(limit).toList();
    }

    public List<Stock> getTopLosers(int limit) {
        return stocks.stream().sorted(Comparator.comparingDouble(Stock::getChangePercent))
                .limit(limit).toList();
    }

    public List<String> getEvents() { return List.copyOf(events); }
    private void addEvent(String event) {
        events.add(0, Instant.now() + " · " + event);
        while (events.size() > 100) events.remove(events.size() - 1);
    }
    public MarketSentiment getSentiment() { return sentiment; }
    public boolean isMarketOpen() { return marketOpen; }
    public void setMarketOpen(boolean open) {
        if (marketOpen != open) addEvent(open ? "Simulated market opened" : "Simulated market closed");
        marketOpen = open;
        listeners.forEach(listener -> SwingUtilities.invokeLater(listener));
    }
    public int getTickMillis() { return tickMillis; }
    public void setTickMillis(int millis) {
        if (millis < 250 || millis > 60_000) throw new IllegalArgumentException("Simulation speed must be between 250 ms and 60 seconds.");
        tickMillis = millis;
    }
    public void addListener(Runnable listener) { listeners.add(listener); }
    public void removeListener(Runnable listener) { listeners.remove(listener); }
    public synchronized void setSentiment(MarketSentiment value) { sentiment = value; }
    public long getTicks() { return ticks; }
    public List<PricePoint> getHistory(Stock stock) { return stock.getPriceHistory(); }

    @Override public synchronized void close() {
        running.set(false);
        if (worker != null) worker.interrupt();
    }
}
