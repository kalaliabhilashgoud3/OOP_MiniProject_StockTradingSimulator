package com.stocksim.util;

import com.stocksim.model.BuyOrder;
import com.stocksim.model.Order;
import com.stocksim.model.OrderStatus;
import com.stocksim.model.OrderType;
import com.stocksim.model.Portfolio;
import com.stocksim.model.PricePoint;
import com.stocksim.model.SellOrder;
import com.stocksim.model.Stock;
import com.stocksim.model.Trade;
import com.stocksim.model.Trader;
import com.stocksim.model.TransactionRecord;
import com.stocksim.model.TransactionType;
import com.stocksim.service.TradeHistory;
import com.stocksim.service.TransactionLedger;
import com.stocksim.service.WatchlistService;
import com.stocksim.service.PerformanceHistory;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** CSV persistence with quoted fields, backup copies, and atomic replacement. */
public final class CsvStore {
    private CsvStore() { }

    public static void saveStocks(Iterable<Stock> stocks, Path file) throws IOException {
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of("symbol", "name", "price", "previousClose", "open", "dayHigh", "dayLow",
                "weekHigh", "weekLow", "volume", "marketCap", "volatility"));
        for (Stock stock : stocks) rows.add(List.of(stock.getSymbol(), stock.getName(), d(stock.getPrice()),
                d(stock.getPreviousClose()), d(stock.getOpen()), d(stock.getDayHigh()), d(stock.getDayLow()),
                d(stock.getWeekHigh()), d(stock.getWeekLow()), Long.toString(stock.getVolume()),
                d(stock.getMarketCap()), d(stock.getVolatility())));
        writeRows(file, rows);
    }

    public static List<Stock> loadStocks(Path file) throws IOException {
        List<List<String>> rows = readRows(file);
        List<Stock> stocks = new ArrayList<>();
        for (List<String> row : dataRows(rows)) {
            if (row.size() < 3) throw new IOException("Stock row must contain symbol, name and price.");
            double price = number(row, 2, 0);
            stocks.add(new Stock(at(row, 0), at(row, 1), price,
                    number(row, 3, price), number(row, 4, price), number(row, 5, price),
                    number(row, 6, price), number(row, 7, price), number(row, 8, price),
                    (long) number(row, 9, 0), number(row, 10, 0), number(row, 11, .015)));
        }
        return stocks;
    }

    public static void saveTraders(Iterable<Trader> traders, Path file) throws IOException {
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of("id", "name", "wallet", "initialBalance"));
        for (Trader trader : traders) rows.add(List.of(trader.getId(), trader.getName(), d(trader.getWallet()), d(trader.getInitialBalance())));
        writeRows(file, rows);
    }

    public static List<Trader> loadTraders(Path file) throws IOException {
        List<Trader> traders = new ArrayList<>();
        for (List<String> row : dataRows(readRows(file))) {
            if (row.size() < 3) throw new IOException("Trader row must contain ID, name and wallet.");
            double wallet = number(row, 2, -1);
            traders.add(new Trader(at(row, 0), at(row, 1), wallet, number(row, 3, wallet)));
        }
        return traders;
    }

    public static void savePortfolios(Iterable<Trader> traders, Path positionsFile,
                                      Path summaryFile) throws IOException {
        List<List<String>> positions = new ArrayList<>();
        positions.add(List.of("traderId", "symbol", "quantity", "averagePrice"));
        List<List<String>> summaries = new ArrayList<>();
        summaries.add(List.of("traderId", "realizedProfitLoss"));
        for (Trader trader : traders) {
            summaries.add(List.of(trader.getId(), d(trader.getPortfolio().getRealizedProfitLoss())));
            trader.getPortfolio().getPositions().values().forEach(position ->
                    positions.add(List.of(trader.getId(), position.symbol(), Integer.toString(position.quantity()),
                            d(position.averagePrice()))));
        }
        writeRows(positionsFile, positions);
        writeRows(summaryFile, summaries);
    }

    public static void loadPortfolios(Iterable<Trader> traders, Path positionsFile,
                                      Path summaryFile) throws IOException {
        Map<String, Trader> byId = new java.util.HashMap<>();
        for (Trader trader : traders) byId.put(trader.getId(), trader);
        if (Files.exists(positionsFile)) {
            for (List<String> row : dataRows(readRows(positionsFile))) {
                if (row.size() < 4) throw new IOException("Portfolio row is incomplete.");
                Trader trader = byId.get(at(row, 0));
                if (trader != null) trader.getPortfolio().restorePosition(at(row, 1),
                        (int) number(row, 2, -1), number(row, 3, -1));
            }
        }
        if (Files.exists(summaryFile)) {
            for (List<String> row : dataRows(readRows(summaryFile))) {
                if (row.size() < 2) throw new IOException("Portfolio summary row is incomplete.");
                Trader trader = byId.get(at(row, 0));
                if (trader != null) trader.getPortfolio().restoreRealizedProfitLoss(number(row, 1, 0));
            }
        }
    }

    public static void saveTrades(TradeHistory history, Path file) throws IOException {
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of("id", "symbol", "buyer", "seller", "quantity", "price", "time"));
        for (Trade trade : history.getAll()) rows.add(List.of(trade.id(), trade.symbol(), trade.buyer(),
                trade.seller(), Integer.toString(trade.quantity()), d(trade.price()), trade.time().toString()));
        writeRows(file, rows);
    }

    public static List<Trade> loadTrades(Path file) throws IOException {
        List<Trade> out = new ArrayList<>();
        for (List<String> row : dataRows(readRows(file))) {
            if (row.size() < 7) throw new IOException("Trade row is incomplete.");
            out.add(new Trade(at(row, 0), at(row, 1), at(row, 2), at(row, 3),
                    (int) number(row, 4, -1), number(row, 5, -1), Instant.parse(at(row, 6))));
        }
        return out;
    }

    public static void saveTransactions(TransactionLedger ledger, Path file) throws IOException {
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of("id", "traderId", "type", "amount", "description", "time"));
        for (TransactionRecord row : ledger.getAll()) rows.add(List.of(row.id(), row.traderId(),
                row.type().name(), d(row.amount()), row.description(), row.time().toString()));
        writeRows(file, rows);
    }

    public static List<TransactionRecord> loadTransactions(Path file) throws IOException {
        List<TransactionRecord> out = new ArrayList<>();
        for (List<String> row : dataRows(readRows(file))) {
            if (row.size() < 6) throw new IOException("Transaction row is incomplete.");
            out.add(new TransactionRecord(at(row, 0), at(row, 1), TransactionType.valueOf(at(row, 2)),
                    number(row, 3, 0), at(row, 4), Instant.parse(at(row, 5))));
        }
        return out;
    }

    public static void saveOrders(Iterable<Order> orders, Path file) throws IOException {
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of("id", "side", "traderId", "symbol", "type", "price", "triggerPrice",
                "originalQuantity", "remainingQuantity", "time", "status"));
        for (Order order : orders) rows.add(List.of(order.getId(), order.getSide(), order.getTrader().getId(),
                order.getStock().getSymbol(), order.getType().name(), d(order.getPrice()),
                d(order.getTriggerPrice()), Integer.toString(order.getOriginalQuantity()),
                Integer.toString(order.getRemainingQuantity()), order.getTime().toString(), order.getStatus().name()));
        writeRows(file, rows);
    }

    public static List<Order> loadOrders(Path file, Map<String, Trader> traders,
                                         Map<String, Stock> stocks) throws IOException {
        List<Order> out = new ArrayList<>();
        for (List<String> row : dataRows(readRows(file))) {
            if (row.size() < 11) throw new IOException("Order row is incomplete.");
            Trader trader = traders.get(at(row, 2));
            Stock stock = stocks.get(at(row, 3));
            if (trader == null || stock == null) continue;
            OrderType type = OrderType.valueOf(at(row, 4));
            String id = at(row, 0);
            double price = number(row, 5, stock.getPrice());
            double trigger = number(row, 6, 0);
            int original = (int) number(row, 7, -1);
            int remaining = (int) number(row, 8, -1);
            Instant time = Instant.parse(at(row, 9));
            OrderStatus status = OrderStatus.valueOf(at(row, 10));
            if ("BUY".equals(at(row, 1))) out.add(new BuyOrder(id, trader, stock, type, price,
                    trigger, original, time, status, remaining));
            else out.add(new SellOrder(id, trader, stock, type, price,
                    trigger, original, time, status, remaining));
        }
        return out;
    }

    public static void saveWatchlists(WatchlistService service, Path file) throws IOException {
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of("traderId", "name", "symbols"));
        service.snapshot().forEach((trader, lists) -> lists.forEach((name, symbols) ->
                rows.add(List.of(trader, name, String.join("|", symbols)))));
        writeRows(file, rows);
    }

    public static List<WatchlistRow> loadWatchlists(Path file) throws IOException {
        List<WatchlistRow> out = new ArrayList<>();
        for (List<String> row : dataRows(readRows(file))) {
            if (row.size() < 3) throw new IOException("Watchlist row is incomplete.");
            List<String> symbols = at(row, 2).isBlank() ? List.of() : List.of(at(row, 2).split("\\|"));
            out.add(new WatchlistRow(at(row, 0), at(row, 1), symbols));
        }
        return out;
    }

    public static void savePriceHistory(Iterable<Stock> stocks, Path file) throws IOException {
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of("symbol", "time", "price", "volume"));
        for (Stock stock : stocks) for (PricePoint point : stock.getPriceHistory()) {
            rows.add(List.of(stock.getSymbol(), point.time().toString(), d(point.price()), Long.toString(point.volume())));
        }
        writeRows(file, rows);
    }

    public static void loadPriceHistory(Iterable<Stock> stocks, Path file) throws IOException {
        Map<String, List<PricePoint>> bySymbol = new java.util.HashMap<>();
        for (List<String> row : dataRows(readRows(file))) {
            if (row.size() < 4) throw new IOException("Price history row is incomplete.");
            bySymbol.computeIfAbsent(at(row, 0), ignored -> new ArrayList<>())
                    .add(new PricePoint(Instant.parse(at(row, 1)), number(row, 2, 0), (long) number(row, 3, 0)));
        }
        for (Stock stock : stocks) if (bySymbol.containsKey(stock.getSymbol())) stock.restoreHistory(bySymbol.get(stock.getSymbol()));
    }

    public static void savePerformance(PerformanceHistory history, Path file) throws IOException {
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of("traderId", "time", "accountValue"));
        history.snapshot().forEach((trader, points) -> {
            for (PerformanceHistory.AccountPoint point : points) {
                rows.add(List.of(trader, point.time().toString(), d(point.value())));
            }
        });
        writeRows(file, rows);
    }

    public static void loadPerformance(PerformanceHistory history, Path file) throws IOException {
        Map<String, List<PerformanceHistory.AccountPoint>> byTrader = new java.util.HashMap<>();
        for (List<String> row : dataRows(readRows(file))) {
            if (row.size() < 3) throw new IOException("Portfolio performance row is incomplete.");
            byTrader.computeIfAbsent(at(row, 0), ignored -> new ArrayList<>())
                    .add(new PerformanceHistory.AccountPoint(Instant.parse(at(row, 1)), number(row, 2, 0)));
        }
        byTrader.forEach(history::restore);
    }

    public static void writeRows(Path file, List<List<String>> rows) throws IOException {
        Path absolute = file.toAbsolutePath();
        Files.createDirectories(absolute.getParent());
        Path backup = absolute.resolveSibling(absolute.getFileName() + ".bak");
        Path temp = absolute.resolveSibling(absolute.getFileName() + ".tmp");
        if (Files.exists(absolute)) {
            try {
                readRows(absolute); // Keep a known-readable version as the backup.
                Files.copy(absolute, backup, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException corruptPrimary) {
                if (!Files.exists(backup)) Files.copy(absolute, backup, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        try (BufferedWriter writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
            for (List<String> row : rows) {
                for (int i = 0; i < row.size(); i++) {
                    if (i > 0) writer.write(',');
                    writer.write(escape(row.get(i)));
                }
                writer.newLine();
            }
        }
        try {
            Files.move(temp, absolute, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(temp, absolute, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static List<List<String>> readRows(Path file) throws IOException {
        try {
            return readRowsDirect(file);
        } catch (IOException primaryError) {
            Path backup = file.resolveSibling(file.getFileName() + ".bak");
            if (!Files.exists(backup)) throw primaryError;
            try {
                List<List<String>> recovered = readRowsDirect(backup);
                Files.copy(backup, file, StandardCopyOption.REPLACE_EXISTING);
                return recovered;
            } catch (IOException backupError) {
                primaryError.addSuppressed(backupError);
                throw primaryError;
            }
        }
    }

    private static List<List<String>> readRowsDirect(Path file) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            List<List<String>> rows = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) rows.add(parse(line));
            return rows;
        }
    }

    private static List<List<String>> dataRows(List<List<String>> rows) {
        return rows.size() <= 1 ? List.of() : rows.subList(1, rows.size());
    }
    private static String at(List<String> row, int index) { return row.get(index).trim(); }
    private static double number(List<String> row, int index, double fallback) {
        if (index >= row.size() || row.get(index).isBlank()) return fallback;
        try { return Double.parseDouble(row.get(index)); }
        catch (NumberFormatException ex) { return fallback; }
    }
    private static String d(double value) { return Double.toString(value); }
    private static String escape(String value) {
        String text = value == null ? "" : value;
        return text.contains(",") || text.contains("\"") || text.contains("\n")
                ? "\"" + text.replace("\"", "\"\"") + "\"" : text;
    }

    private static List<String> parse(String line) throws IOException {
        List<String> values = new ArrayList<>();
        StringBuilder value = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    value.append('"'); i++;
                } else quoted = !quoted;
            } else if (ch == ',' && !quoted) {
                values.add(value.toString()); value.setLength(0);
            } else value.append(ch);
        }
        if (quoted) throw new IOException("CSV row contains an unclosed quoted value.");
        values.add(value.toString());
        return values;
    }

    public record WatchlistRow(String traderId, String name, List<String> symbols) { }
}
