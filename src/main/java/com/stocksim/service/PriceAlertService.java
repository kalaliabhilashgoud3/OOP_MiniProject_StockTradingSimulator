package com.stocksim.service;

import com.stocksim.model.Stock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** One-shot price alerts for the offline simulated market. */
public class PriceAlertService {
    public enum Direction { ABOVE, BELOW }
    public record Alert(String id, String traderId, String symbol, Direction direction,
                        double targetPrice, boolean active) { }

    private final Map<String, Alert> alerts = new LinkedHashMap<>();

    public synchronized Alert add(String traderId, String symbol, Direction direction, double targetPrice) {
        if (traderId == null || traderId.isBlank() || symbol == null || symbol.isBlank() || direction == null
                || !Double.isFinite(targetPrice) || targetPrice <= 0) {
            throw new IllegalArgumentException("Choose a stock and enter a positive alert price.");
        }
        Alert alert = new Alert(UUID.randomUUID().toString().substring(0, 8), traderId,
                symbol.toUpperCase(), direction, targetPrice, true);
        alerts.put(alert.id(), alert);
        return alert;
    }

    public synchronized List<String> check(Stock stock) {
        List<String> triggered = new ArrayList<>();
        for (Alert alert : new ArrayList<>(alerts.values())) {
            if (!alert.active() || !alert.symbol().equals(stock.getSymbol())) continue;
            boolean hit = alert.direction() == Direction.ABOVE
                    ? stock.getPrice() >= alert.targetPrice() : stock.getPrice() <= alert.targetPrice();
            if (hit) {
                alerts.put(alert.id(), new Alert(alert.id(), alert.traderId(), alert.symbol(),
                        alert.direction(), alert.targetPrice(), false));
                triggered.add("Price alert · " + stock.getSymbol() + " reached " + stock.getPrice()
                        + " (target " + alert.direction().name().toLowerCase() + " " + alert.targetPrice() + ")");
            }
        }
        return List.copyOf(triggered);
    }

    public synchronized void restore(Alert alert) { alerts.put(alert.id(), alert); }
    public synchronized List<Alert> getAlerts() { return List.copyOf(alerts.values()); }
}
