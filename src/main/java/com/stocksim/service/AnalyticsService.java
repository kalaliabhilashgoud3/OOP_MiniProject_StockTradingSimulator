package com.stocksim.service;

import com.stocksim.model.Stock;
import com.stocksim.model.Trade;
import com.stocksim.model.Trader;
import com.stocksim.model.TransactionRecord;
import com.stocksim.model.TransactionType;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Computes account analytics from recorded trades, positions, and ledger entries. */
public class AnalyticsService {
    private final List<Trade> trades;
    private final List<TransactionRecord> transactions;
    private final Map<String, Stock> stocks;

    public AnalyticsService(List<Trade> trades, List<TransactionRecord> transactions,
                            Map<String, Stock> stocks) {
        this.trades = trades;
        this.transactions = transactions;
        this.stocks = stocks;
    }

    public Metrics forTrader(Trader trader) {
        String id = trader.getId();
        List<Trade> owned = trades.stream().filter(t -> t.buyer().equals(id) || t.seller().equals(id)).toList();
        List<TransactionRecord> realized = transactions.stream()
                .filter(t -> t.traderId().equals(id) && t.type() == TransactionType.REALIZED_PNL).toList();
        long wins = realized.stream().filter(t -> t.amount() > 0).count();
        long losses = realized.stream().filter(t -> t.amount() < 0).count();
        double avgWin = realized.stream().filter(t -> t.amount() > 0).mapToDouble(TransactionRecord::amount)
                .average().orElse(0);
        double avgLoss = realized.stream().filter(t -> t.amount() < 0).mapToDouble(TransactionRecord::amount)
                .average().orElse(0);
        double unrealized = trader.getPortfolio().getPositions().values().stream().mapToDouble(position -> {
            Stock stock = stocks.get(position.symbol());
            return stock == null ? 0 : (stock.getPrice() - position.averagePrice()) * position.quantity();
        }).sum();
        double realizedTotal = trader.getPortfolio().getRealizedProfitLoss();
        double invested = trader.getPortfolio().cost();
        double marketValue = trader.getPortfolio().value(stocks);
        String best = bestStock(id, true);
        String worst = bestStock(id, false);
        return new Metrics(owned.size(), wins, losses,
                wins + losses == 0 ? 0 : wins * 100.0 / (wins + losses), avgWin, avgLoss,
                best, worst, realizedTotal, unrealized, invested, marketValue,
                marketValue + trader.getWallet(), trader.getWallet(),
                realizedSince(id, Instant.now().minus(1, ChronoUnit.DAYS)),
                realizedSince(id, Instant.now().minus(7, ChronoUnit.DAYS)),
                realizedSince(id, Instant.now().minus(30, ChronoUnit.DAYS)));
    }

    private double realizedSince(String traderId, Instant since) {
        return transactions.stream().filter(t -> t.traderId().equals(traderId)
                        && t.type() == TransactionType.REALIZED_PNL && t.time().isAfter(since))
                .mapToDouble(TransactionRecord::amount).sum();
    }

    private String bestStock(String traderId, boolean highest) {
        Map<String, Double> byStock = new java.util.HashMap<>();
        for (TransactionRecord item : transactions) {
            if (item.traderId().equals(traderId) && item.type() == TransactionType.REALIZED_PNL) {
                String[] parts = item.description().split(" ", 2);
                if (parts.length > 1) byStock.merge(parts[1], item.amount(), Double::sum);
            }
        }
        Comparator<Map.Entry<String, Double>> comparator = Map.Entry.comparingByValue();
        if (highest) comparator = comparator.reversed();
        return byStock.entrySet().stream().sorted(comparator).map(Map.Entry::getKey).findFirst().orElse("—");
    }

    public record Metrics(int totalTrades, long winningTrades, long losingTrades, double winRate,
                          double averageProfit, double averageLoss, String bestStock, String worstStock,
                          double realizedProfitLoss, double unrealizedProfitLoss, double investedAmount,
                          double marketValue, double accountValue, double availableCash,
                          double dailyProfitLoss, double weeklyProfitLoss, double monthlyProfitLoss) { }
}
