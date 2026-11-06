package com.stocksim.service;

import com.stocksim.model.Stock;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicBoolean;

/** Runs the offline market engine on a background thread with configurable speed. */
public class PriceUpdater implements Runnable, AutoCloseable {
    private final MarketEngine engine;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private volatile int delayMillis = 2_000;
    private volatile Thread worker;

    public PriceUpdater(MarketEngine engine) { this.engine = engine; }
    public PriceUpdater(Collection<Stock> stocks) { this(new MarketEngine(stocks)); }
    public MarketEngine getEngine() { return engine; }

    public void setDelayMillis(int delayMillis) {
        engine.setTickMillis(delayMillis);
        this.delayMillis = delayMillis;
        Thread current = worker;
        if (current != null) current.interrupt();
    }

    public void stop() {
        running.set(false);
        Thread current = worker;
        if (current != null) current.interrupt();
    }

    @Override public void run() {
        worker = Thread.currentThread();
        while (running.get()) {
            if (engine.isMarketOpen()) engine.tickOnce();
            try {
                Thread.sleep(delayMillis);
            } catch (InterruptedException ex) {
                if (!running.get()) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    @Override public void close() { stop(); }
}
