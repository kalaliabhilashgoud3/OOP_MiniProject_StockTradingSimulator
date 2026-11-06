package com.stocksim.service;

import com.stocksim.exception.InsufficientFundsException;
import com.stocksim.model.BuyOrder;
import com.stocksim.model.Order;
import com.stocksim.model.OrderType;
import com.stocksim.model.Portfolio;
import com.stocksim.model.SellOrder;
import com.stocksim.model.Stock;
import com.stocksim.model.Trade;
import com.stocksim.model.Trader;
import com.stocksim.model.TransactionRecord;
import com.stocksim.service.WatchlistService;
import com.stocksim.util.CsvStore;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Coordinates market, accounts, matching, analytics data, and CSV persistence. */
public class TradingPlatform implements AutoCloseable {
    private final Path dataDirectory;
    private final Map<String, Stock> stocks = new LinkedHashMap<>();
    private final Map<String, Trader> traders = new LinkedHashMap<>();
    private final TradeHistory tradeHistory = new TradeHistory();
    private final TransactionLedger transactionLedger = new TransactionLedger();
    private final PerformanceHistory performanceHistory = new PerformanceHistory();
    private final WatchlistService watchlists = new WatchlistService();
    private final AccountService accounts = new AccountService(transactionLedger);
    private final OrderBook orderBook = new OrderBook(tradeHistory, transactionLedger);
    private final MarketEngine market;
    private final PriceUpdater priceUpdater;

    private TradingPlatform(Path dataDirectory) throws IOException {
        this.dataDirectory = dataDirectory;
        loadStocks();
        loadTraders();
        loadHistory();
        loadAccountsAndOrders();
        loadWatchlists();
        market = new MarketEngine(stocks.values());
        market.addListener(() -> {
            stocks.values().forEach(orderBook::onPriceTick);
            performanceHistory.record(traders.values(), stocks);
        });
        priceUpdater = new PriceUpdater(market);
        loadPreferences();
    }

    public static TradingPlatform open(Path dataDirectory) throws IOException {
        Files.createDirectories(dataDirectory);
        return new TradingPlatform(dataDirectory);
    }

    private void loadStocks() throws IOException {
        Path file = dataDirectory.resolve("stocks.csv");
        if (Files.exists(file)) for (Stock stock : CsvStore.loadStocks(file)) stocks.put(stock.getSymbol(), stock);
        StockCatalog.simulatedIndianStocks().forEach(stocks::putIfAbsent);
        // Keep earlier classroom sample listings during migration to the Indian-market catalog.
        stocks.putIfAbsent("MNR", new Stock("MNR", "MNR Industries", 250));
        stocks.putIfAbsent("TECH", new Stock("TECH", "Tech Solutions", 420));
        stocks.putIfAbsent("BANK", new Stock("BANK", "Banking Corp", 180));
    }

    private void loadTraders() throws IOException {
        Path file = dataDirectory.resolve("traders.csv");
        if (Files.exists(file)) {
            for (Trader trader : CsvStore.loadTraders(file)) traders.put(trader.getId(), trader);
        } else {
            traders.put("T001", new Trader("T001", "Aarav", 100_000));
            traders.put("T002", new Trader("T002", "Diya", 100_000));
            traders.put("T003", new Trader("T003", "Kabir", 75_000));
            traders.put("T004", new Trader("T004", "Meera", 80_000));
            traders.put("T005", new Trader("T005", "Ishaan", 50_000));
        }
    }

    private void loadHistory() throws IOException {
        Path trades = dataDirectory.resolve("trades.csv");
        if (Files.exists(trades)) tradeHistory.addAll(CsvStore.loadTrades(trades));
        Path ledger = dataDirectory.resolve("transactions.csv");
        if (Files.exists(ledger)) transactionLedger.addAll(CsvStore.loadTransactions(ledger));
        Path history = dataDirectory.resolve("price_history.csv");
        if (Files.exists(history)) CsvStore.loadPriceHistory(stocks.values(), history);
        Path performance = dataDirectory.resolve("performance.csv");
        if (Files.exists(performance)) CsvStore.loadPerformance(performanceHistory, performance);
    }

    private void loadAccountsAndOrders() throws IOException {
        Path positions = dataDirectory.resolve("portfolio.csv");
        Path summaries = dataDirectory.resolve("portfolio_summary.csv");
        if (Files.exists(positions) || Files.exists(summaries)) {
            CsvStore.loadPortfolios(traders.values(), positions, summaries);
        } else {
            // One-time migration: the earlier simulator showed demo MNR holdings but did not save them.
            if (traders.containsKey("T002") && stocks.containsKey("MNR")) {
                traders.get("T002").getPortfolio().add("MNR", 20, stocks.get("MNR").getPrice());
            }
            if (traders.containsKey("T004") && stocks.containsKey("MNR")) {
                traders.get("T004").getPortfolio().add("MNR", 15, stocks.get("MNR").getPrice());
            }
        }

        // Old orders.csv is kept as sample data; active orders are stored separately.
        Path activeOrders = dataDirectory.resolve("active_orders.csv");
        if (Files.exists(activeOrders)) {
            for (Order order : CsvStore.loadOrders(activeOrders, traders, stocks)) orderBook.restore(order);
        }
    }

    private void loadWatchlists() throws IOException {
        Path file = dataDirectory.resolve("watchlists.csv");
        if (Files.exists(file)) {
            for (CsvStore.WatchlistRow row : CsvStore.loadWatchlists(file)) {
                watchlists.restore(row.traderId(), row.name(), row.symbols());
            }
        }
        for (Trader trader : traders.values()) {
            if (watchlists.getNames(trader.getId()).isEmpty()) watchlists.create(trader.getId(), "My Watchlist");
        }
    }

    private void loadPreferences() throws IOException {
        Path file = dataDirectory.resolve("preferences.csv");
        if (!Files.exists(file)) return;
        for (List<String> row : CsvStore.readRows(file)) {
            if (row.size() < 2 || row.get(0).equalsIgnoreCase("key")) continue;
            switch (row.get(0)) {
                case "marketOpen" -> market.setMarketOpen(Boolean.parseBoolean(row.get(1)));
                case "tickMillis" -> {
                    try { priceUpdater.setDelayMillis(Integer.parseInt(row.get(1))); }
                    catch (NumberFormatException ignored) { /* Keep the safe default when the preference is invalid. */ }
                }
                default -> { /* Ignore settings added by a later version. */ }
            }
        }
    }

    public synchronized void save() throws IOException {
        CsvStore.saveStocks(stocks.values(), dataDirectory.resolve("stocks.csv"));
        CsvStore.saveTraders(traders.values(), dataDirectory.resolve("traders.csv"));
        CsvStore.savePortfolios(traders.values(), dataDirectory.resolve("portfolio.csv"),
                dataDirectory.resolve("portfolio_summary.csv"));
        CsvStore.saveTrades(tradeHistory, dataDirectory.resolve("trades.csv"));
        CsvStore.saveTransactions(transactionLedger, dataDirectory.resolve("transactions.csv"));
        CsvStore.saveOrders(orderBook.getOrders(), dataDirectory.resolve("active_orders.csv"));
        CsvStore.saveWatchlists(watchlists, dataDirectory.resolve("watchlists.csv"));
        CsvStore.savePriceHistory(stocks.values(), dataDirectory.resolve("price_history.csv"));
        CsvStore.savePerformance(performanceHistory, dataDirectory.resolve("performance.csv"));
        CsvStore.writeRows(dataDirectory.resolve("preferences.csv"), List.of(
                List.of("key", "value"),
                List.of("marketOpen", Boolean.toString(market.isMarketOpen())),
                List.of("tickMillis", Integer.toString(market.getTickMillis()))));
    }

    public Map<String, Stock> getStocks() { return java.util.Collections.unmodifiableMap(stocks); }
    public List<Stock> getStockList() { return List.copyOf(stocks.values()); }
    public Optional<Stock> findStock(String symbol) { return Optional.ofNullable(stocks.get(symbol.toUpperCase())); }
    public Map<String, Trader> getTraders() { return java.util.Collections.unmodifiableMap(traders); }
    public List<Trader> getTraderList() { return List.copyOf(traders.values()); }
    public Trader getTrader(String id) {
        Trader trader = traders.get(id);
        if (trader == null) throw new IllegalArgumentException("Trader account was not found.");
        return trader;
    }
    public TradeHistory getTradeHistory() { return tradeHistory; }
    public TransactionLedger getTransactionLedger() { return transactionLedger; }
    public PerformanceHistory getPerformanceHistory() { return performanceHistory; }
    public WatchlistService getWatchlists() { return watchlists; }
    public AccountService getAccounts() { return accounts; }
    public OrderBook getOrderBook() { return orderBook; }
    public MarketEngine getMarket() { return market; }
    public PriceUpdater getPriceUpdater() { return priceUpdater; }

    public Order submitOrder(String traderId, String symbol, boolean buy, OrderType type,
                             int quantity, double limitPrice, double triggerPrice)
            throws InsufficientFundsException {
        Stock stock = findStock(symbol).orElseThrow(() -> new IllegalArgumentException("Choose a listed stock."));
        Trader trader = getTrader(traderId);
        double price = type == OrderType.MARKET ? stock.getPrice() : limitPrice;
        Order order = buy
                ? new BuyOrder(trader, stock, type, quantity, price, triggerPrice)
                : new SellOrder(trader, stock, type, quantity, price, triggerPrice);
        return orderBook.submit(order);
    }

    @Override public void close() throws IOException {
        priceUpdater.stop();
        market.close();
        save();
    }
}
